package com.voxelbridge.export.exporter;

import com.voxelbridge.config.ExportRuntimeConfig;
import com.voxelbridge.core.ir.IrSink;
import com.voxelbridge.core.util.geometry.GeometryUtil;
import com.voxelbridge.export.CoordinateMode;
import com.voxelbridge.export.ExportContext;
import com.voxelbridge.export.exporter.blockentity.BlockEntityExportResult;
import com.voxelbridge.export.exporter.blockentity.BlockEntityExporter;
import com.voxelbridge.export.exporter.blockentity.BlockEntityRenderBatch;
import com.voxelbridge.export.util.geometry.VertexExtractor;
import com.voxelbridge.modhandler.ModHandledQuads;
import com.voxelbridge.modhandler.ModHandlerRegistry;
import com.voxelbridge.util.debug.LogModule;
import com.voxelbridge.util.debug.VoxelBridgeLogger;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * Simplified block geometry exporter.
 * Delegates specialized tasks to dedicated managers and processors.
 */
public final class BlockExporter {
    private final ExportContext ctx;
    private final IrSink sceneSink;
    private final IrSink blockEntitySceneSink;
    private final Level level;
    private final ClientChunkCache chunkCache;
    private final boolean vanillaRandomTransformEnabled;
    private final BlockEntityRenderBatch blockEntityBatch;
    private final PlaneOffsetTracker planeOffsetTracker = new PlaneOffsetTracker();

    private BlockPos regionMin;
    private BlockPos regionMax;
    private double offsetX = 0;
    private double offsetY = 0;
    private double offsetZ = 0;

    // Managers for specialized tasks
    private OverlayManager overlayManager;
    private QuadProcessor quadProcessor;

    private final BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
    private volatile boolean missingNeighborDetected = false;

    public BlockExporter(ExportContext ctx, IrSink sceneSink, Level level) {
        this(ctx, sceneSink, level, null, sceneSink);
    }

    public BlockExporter(ExportContext ctx, IrSink sceneSink, Level level, BlockEntityRenderBatch blockEntityBatch) {
        this(ctx, sceneSink, level, blockEntityBatch, sceneSink);
    }

    public BlockExporter(ExportContext ctx, IrSink sceneSink, Level level, BlockEntityRenderBatch blockEntityBatch, IrSink blockEntitySceneSink) {
        this.ctx = ctx;
        this.sceneSink = sceneSink;
        this.blockEntitySceneSink = blockEntitySceneSink != null ? blockEntitySceneSink : sceneSink;
        this.level = level;
        this.chunkCache = (level instanceof ClientLevel cl) ? cl.getChunkSource() : null;
        this.vanillaRandomTransformEnabled = ctx.isVanillaRandomTransformEnabled();
        this.blockEntityBatch = blockEntityBatch;
    }

    public void setRegionBounds(BlockPos min, BlockPos max) {
        this.regionMin = min;
        this.regionMax = max;

        if (ctx.getCoordinateMode() == CoordinateMode.CENTERED) {
            offsetX = -(min.getX() + max.getX()) / 2.0;
            offsetY = -(min.getY() + max.getY()) / 2.0;
            offsetZ = -(min.getZ() + max.getZ()) / 2.0;
        } else {
            offsetX = 0;
            offsetY = 0;
            offsetZ = 0;
        }

        // Initialize managers with current offsets
        this.overlayManager = new OverlayManager(ctx, level, offsetX, offsetY, offsetZ, planeOffsetTracker);
        this.quadProcessor = new QuadProcessor(ctx, level, sceneSink, offsetX, offsetY, offsetZ, planeOffsetTracker);
    }

    /**
     * Samples a single block and outputs its geometry.
     */
    public void sampleBlock(BlockState state, BlockPos pos) {
        // Check neighbor chunks are loaded only for chunk-edge blocks
        int localX = pos.getX() & 15;
        int localZ = pos.getZ() & 15;
        if ((localX == 0 || localX == 15 || localZ == 0 || localZ == 15) && !isNeighborChunksLoadedForBlock(pos)) {
            VoxelBridgeLogger.debug(LogModule.SAMPLER_BLOCK, "[BlockExporter] Neighbor chunks missing for block at " + pos.toShortString());
            missingNeighborDetected = true;
            return;
        }

        // Clear per-block caches
        overlayManager.clear();
        quadProcessor.clear();
        planeOffsetTracker.clear();

        if (state.isAir()) return;

        // Vanilla random offset (grass, fern, etc.)
        Vec3 randomOffset = vanillaRandomTransformEnabled ? state.getOffset(level, pos) : Vec3.ZERO;

        // Export fluid
        FluidState fluidState = state.getFluidState();
        if (fluidState != null && !fluidState.isEmpty()) {
            FluidExporter.sample(ctx, sceneSink, level, state, pos, fluidState,
                offsetX, offsetY, offsetZ, regionMin, regionMax);
        }

        // Export block entity
        BlockEntity be = level.getBlockEntity(pos);
        if (be != null) {
            VoxelBridgeLogger.debug(LogModule.BLOCKENTITY, "[BlockExporter] Found BlockEntity: " + be.getClass().getSimpleName() + " at " + pos.toShortString() + ", isExportEnabled=" + ctx.isBlockEntityExportEnabled());
        }
        if (be != null && ctx.isBlockEntityExportEnabled()) {
            VoxelBridgeLogger.debug(LogModule.BLOCKENTITY, "[BlockExporter] Calling BlockEntityExporter.export for " + be.getClass().getSimpleName());
            BlockEntityExportResult beResult = BlockEntityExporter.export(ctx, level, state, be, pos,
                blockEntitySceneSink, offsetX, offsetY, offsetZ, blockEntityBatch);
            VoxelBridgeLogger.debug(LogModule.BLOCKENTITY, "[BlockExporter] BlockEntityExporter.export returned: rendered=" + beResult.rendered() + ", replaceBlockModel=" + beResult.replaceBlockModel());
            if (beResult.replaceBlockModel()) return;
        }

        // Skip invisible blocks
        if (state.getRenderShape() == RenderShape.INVISIBLE) return;

        // Get block model via Adapter
        BakedModel model = com.voxelbridge.adapter.Adapters.getRender().getBlockModel(state);
        if (model == null) return;

        // Occlusion culling for opaque blocks
        boolean isTransparent = !state.isSolidRender(level, pos);
        byte[] faceOcclusionCache = null;
        if (!isTransparent) {
            faceOcclusionCache = new byte[Direction.values().length];
            boolean fullyOccluded = true;
            for (Direction dir : Direction.values()) {
                int idx = dir.ordinal();
                mutablePos.setWithOffset(pos, dir);
                if (isOutsideRegion(mutablePos)) {
                    faceOcclusionCache[idx] = 2;
                    fullyOccluded = false;
                    continue;
                }
                boolean occluded = isNeighborSolid(mutablePos);
                faceOcclusionCache[idx] = (byte) (occluded ? 1 : 2);
                if (!occluded) fullyOccluded = false;
            }
            if (fullyOccluded) return;
        }

        // Get quads via Adapter (handles ModelData and Fabric API internally)
        ModHandledQuads handledQuads = ModHandlerRegistry.shouldHandle(be)
            ? ModHandlerRegistry.handle(ctx, level, state, be, pos, model)
            : null;
        List<BakedQuad> quads;
        if (handledQuads != null) {
            quads = handledQuads.quads();
        } else {
            long seed = state.is(Blocks.LILY_PAD)
                ? GeometryUtil.computeBushSeed(pos.getX(), pos.getY(), pos.getZ())
                : Mth.getSeed(pos.getX(), pos.getY(), pos.getZ());
            quads = com.voxelbridge.adapter.Adapters.getRender().getQuads(model, state, pos, level, seed);
        }

        if (quads.isEmpty()) return;

        int quadCount = quads.size();
        String[] spriteKeys = new String[quadCount];
        boolean hasCtmSprite = false;
        boolean isCtmCompact = false;
        for (int i = 0; i < quadCount; i++) {
            BakedQuad quad = quads.get(i);
            if (quad == null || quad.getSprite() == null) continue;
            String spriteKey = com.voxelbridge.adapter.Adapters.getRender().getSpriteName(quad.getSprite());
            spriteKeys[i] = spriteKey;
            if (!hasCtmSprite && isCtmOverlaySprite(spriteKey)) {
                hasCtmSprite = true;
            }
            if (!isCtmCompact && isContinuitySprite(spriteKey)) {
                isCtmCompact = true;
            }
        }

        // Generate material key
        String blockKey = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        int lightLevel = state.getLightEmission();
        if (lightLevel > 0) {
            blockKey = blockKey + "_emissive";
        }

        ctx.registerSpriteMaterial(blockKey, blockKey);

        // PASS 1: Position-based CTM overlay detection (skip if no CTM sprites)
        if (hasCtmSprite && quadCount > 1) {
            detectCtmOverlaysByPosition(quads, spriteKeys, state, pos, blockKey, randomOffset);
        }

        // PASS 1b: Detect and cache overlays
        for (int i = 0; i < quadCount; i++) {
            BakedQuad quad = quads.get(i);
            String spriteKey = spriteKeys[i];
            if (quad == null || spriteKey == null) continue;

            // Check vanilla overlay
            if (OverlayManager.isVanillaOverlay(spriteKey)) {
                String vanillaBase = OverlayManager.extractVanillaOverlayBase(spriteKey);
                if (vanillaBase == null) vanillaBase = blockKey;
                overlayManager.cacheOverlay(vanillaBase, state, pos, quad, randomOffset, spriteKey);
                continue;  // Skip this quad in PASS 2
            }

            // Check hilight overlay
            if (isHilightOverlay(spriteKey)) {
                // Hilight: bypass overlay suffix, let OverlayManager use sprite name (_hilight) for material/offset
                overlayManager.cacheOverlayNoMarkup(blockKey, state, pos, quad, randomOffset, spriteKey);
                continue;  // Skip this quad in PASS 2
            }
        }

        // PASS 2: Process base quads
        byte[] sameBlockOcclusionCache = null;
        for (int i = 0; i < quadCount; i++) {
            BakedQuad quad = quads.get(i);
            String spriteKey = spriteKeys[i];
            if (quad == null || spriteKey == null) continue;

            Direction dir = quad.getDirection();

            // Skip if processed as overlay
            if (overlayManager.isProcessedOverlay(spriteKey)) {
                continue;
            }

            // Occlusion culling
            if (dir != null) {
                if (!isTransparent) {
                    // Opaque blocks: cull if neighbor is solid
                    boolean occluded = isFaceOccludedCached(pos, dir, faceOcclusionCache);
                    if (occluded) continue;
                } else if (isCtmCompact) {
                    // CTM compact: cull internal faces against same block to keep outer shell
                    int idx = dir.ordinal();
                    if (sameBlockOcclusionCache == null) {
                        sameBlockOcclusionCache = new byte[Direction.values().length];
                    }
                    byte cached = sameBlockOcclusionCache[idx];
                    boolean occluded;
                    if (cached == 0) {
                        occluded = isFaceOccludedBySameBlock(state, pos, dir);
                        sameBlockOcclusionCache[idx] = (byte) (occluded ? 1 : 2);
                    } else {
                        occluded = cached == 1;
                    }
                    if (occluded) continue;
                }
                // Transparent blocks: no face culling (internal faces must remain visible)
            }

            // Process quad
            quadProcessor.processQuad(state, pos, quad, blockKey, randomOffset);
        }

        // PASS 3: Output overlays with culling
        final boolean ctmCompact = isCtmCompact;
        final byte[] occlusionCacheFinal = faceOcclusionCache;
        final byte[] sameBlockCacheFinal = sameBlockOcclusionCache;
        overlayManager.outputOverlays(sceneSink, state, dir -> {
            if (dir == null) return false;
            if (!isTransparent) {
                return isFaceOccludedCached(pos, dir, occlusionCacheFinal);
            }
            if (ctmCompact) {
                int idx = dir.ordinal();
                if (sameBlockCacheFinal == null) {
                    return isFaceOccludedBySameBlock(state, pos, dir);
                }
                byte cached = sameBlockCacheFinal[idx];
                if (cached == 1) return true;
                if (cached == 2) return false;
                boolean occluded = isFaceOccludedBySameBlock(state, pos, dir);
                sameBlockCacheFinal[idx] = (byte) (occluded ? 1 : 2);
                return occluded;
            }
            // Transparent blocks: no face culling for overlays
            return false;
        });
    }

    private void detectCtmOverlaysByPosition(List<BakedQuad> quads, String[] spriteKeys, BlockState state, BlockPos pos, String blockKey, Vec3 randomOffset) {
        record QuadEntry(int index, BakedQuad quad, String spriteKey, long posHash, boolean approxSquare,
                         float uMin, float uMax, float vMin, float vMax) {}

        Map<Long, List<QuadEntry>> groups = new HashMap<>(quads.size());
        float[] positions = new float[12];
        float[] uv = new float[8];
        float[] uvBounds = new float[4];

        for (int i = 0; i < quads.size(); i++) {
            BakedQuad quad = quads.get(i);
            String spriteKey = spriteKeys[i];
            if (quad == null || spriteKey == null) continue;
            var sprite = quad.getSprite();
            VertexExtractor.extractPositionsUv(quad, pos, sprite, offsetX, offsetY, offsetZ, randomOffset, positions, uv, null);
            long posHash = GeometryUtil.computePositionHash(positions);
            boolean approxSquare = GeometryUtil.isApproxAxisAlignedSquare(positions, 0.01f);

            if (!GeometryUtil.computeUvBounds(uv, uvBounds)) {
                continue;
            }
            float uMin = uvBounds[0];
            float uMax = uvBounds[1];
            float vMin = uvBounds[2];
            float vMax = uvBounds[3];

            groups.computeIfAbsent(posHash, k -> new ArrayList<>())
                .add(new QuadEntry(i, quad, spriteKey, posHash, approxSquare, uMin, uMax, vMin, vMax));
        }

        for (List<QuadEntry> group : groups.values()) {
            if (group.size() < 2) continue;

            // CTM overlay detection: square quad + CTM sprite.
            boolean hasCtmSprite = group.stream().anyMatch(q -> isCtmOverlaySprite(q.spriteKey()));
            QuadEntry first = group.get(0);
            if (!hasCtmSprite || !first.approxSquare()) continue;

            // Allow any rectangle as long as the UV AABB size matches (same shape).
            float du = first.uMax - first.uMin;
            float dv = first.vMax - first.vMin;
            final float UV_EPS = 1e-4f;
            boolean sameShape = group.stream().allMatch(q -> Math.abs((q.uMax - q.uMin) - du) < UV_EPS
                && Math.abs((q.vMax - q.vMin) - dv) < UV_EPS);
            if (!sameShape) continue;

            int minIndex = group.stream().mapToInt(QuadEntry::index).min().orElse(Integer.MAX_VALUE);

            group.stream()
                .sorted(Comparator.comparingInt(QuadEntry::index))
                .filter(q -> q.index() != minIndex)
                .forEach(q -> overlayManager.cacheOverlay(blockKey, state, pos, q.quad(), randomOffset, q.spriteKey()));
        }
    }

    private boolean isCtmOverlaySprite(String spriteKey) {
        if (spriteKey == null) return false;
        String lower = spriteKey.toLowerCase(Locale.ROOT);
        return lower.matches(".*_\\d+$")
            || lower.contains("/ctm/")
            || lower.contains("ctm/")
            || lower.contains("continuity");
    }

    private boolean isContinuitySprite(String spriteKey) {
        if (spriteKey == null) return false;
        return spriteKey.toLowerCase(Locale.ROOT).contains("continuity");
    }

    private boolean isHilightOverlay(String spriteKey) {
        if (spriteKey == null) return false;
        return spriteKey.toLowerCase(Locale.ROOT).contains("_hilight");
    }

    // ===== Occlusion culling helpers =====

    private boolean isNeighborChunksLoadedForBlock(BlockPos pos) {
        if (chunkCache == null) return true;

        int localX = pos.getX() & 15;
        int localZ = pos.getZ() & 15;
        int cx = pos.getX() >> 4;
        int cz = pos.getZ() >> 4;

        if (localX == 0 && isChunkMissing(cx - 1, cz)) return false;
        if (localX == 15 && isChunkMissing(cx + 1, cz)) return false;
        if (localZ == 0 && isChunkMissing(cx, cz - 1)) return false;
        return localZ != 15 || !isChunkMissing(cx, cz + 1);
    }

    private boolean isChunkMissing(int cx, int cz) {
        var chunk = chunkCache.getChunk(cx, cz, false);
        return chunk == null || chunk.isEmpty();
    }

    private boolean isFullyOccluded(BlockPos pos) {
        for (Direction dir : Direction.values()) {
            mutablePos.setWithOffset(pos, dir);
            if (isOutsideRegion(mutablePos)) return false;
            if (!isNeighborSolid(mutablePos)) return false;
        }
        return true;
    }

    private boolean isFaceOccluded(BlockPos pos, Direction face) {
        mutablePos.setWithOffset(pos, face);
        if (isOutsideRegion(mutablePos)) return false;
        return isNeighborSolid(mutablePos);
    }

    private boolean isFaceOccludedCached(BlockPos pos, Direction face, byte[] cache) {
        if (cache == null) {
            return isFaceOccluded(pos, face);
        }
        int idx = face.ordinal();
        byte cached = cache[idx];
        if (cached == 1) return true;
        if (cached == 2) return false;
        boolean occluded = isFaceOccluded(pos, face);
        cache[idx] = (byte) (occluded ? 1 : 2);
        return occluded;
    }

    private boolean isFaceOccludedBySameBlock(BlockState state, BlockPos pos, Direction face) {
        mutablePos.setWithOffset(pos, face);
        if (isOutsideRegion(mutablePos)) return false;
        return isNeighborSameBlock(state, mutablePos);
    }

    private boolean isOutsideRegion(BlockPos pos) {
        if (regionMin == null || regionMax == null) return false;
        return pos.getX() < regionMin.getX() || pos.getX() > regionMax.getX()
            || pos.getY() < regionMin.getY() || pos.getY() > regionMax.getY()
            || pos.getZ() < regionMin.getZ() || pos.getZ() > regionMax.getZ();
    }

    private boolean isNeighborSolid(BlockPos neighbor) {
        BlockState state;
        if (chunkCache != null) {
            int cx = neighbor.getX() >> 4;
            int cz = neighbor.getZ() >> 4;
            var chunk = chunkCache.getChunk(cx, cz, false);
            if (chunk == null || chunk.isEmpty()) return true;
            state = chunk.getBlockState(neighbor);
        } else {
            state = level.getBlockState(neighbor);
        }

        // FILLCAVE: Treat any air with skylight 0 as solid for occlusion culling
        if (ExportRuntimeConfig.isFillCaveEnabled()) {
            if (state.isAir() && level.getBrightness(LightLayer.SKY, neighbor) == 0) {
                return true; // Pretend ALL cave_air is solid
            }
        }

        return state.isSolidRender(level, neighbor);
    }

    private boolean isNeighborSameBlock(BlockState state, BlockPos neighbor) {
        BlockState neighborState;
        if (chunkCache != null) {
            int cx = neighbor.getX() >> 4;
            int cz = neighbor.getZ() >> 4;
            var chunk = chunkCache.getChunk(cx, cz, false);
            if (chunk == null || chunk.isEmpty()) return false;
            neighborState = chunk.getBlockState(neighbor);
        } else {
            neighborState = level.getBlockState(neighbor);
        }
        return neighborState.getBlock() == state.getBlock();
    }

    public boolean hadMissingNeighborAndReset() {
        boolean result = missingNeighborDetected;
        missingNeighborDetected = false;
        return result;
    }
}
