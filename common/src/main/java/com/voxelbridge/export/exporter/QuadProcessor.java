package com.voxelbridge.export.exporter;

import com.voxelbridge.core.ir.IrSink;
import com.voxelbridge.core.ir.RenderLayer;
import com.voxelbridge.core.ir.TintMode;
import com.voxelbridge.core.util.color.ColorUtil;
import com.voxelbridge.core.util.color.ColorMode;
import com.voxelbridge.core.util.color.ColorModeHandler;
import com.voxelbridge.compat.BlockStateCompat;
import com.voxelbridge.config.ExportRuntimeConfig;
import com.voxelbridge.export.ExportContext;
import com.voxelbridge.export.quad.QuadData;
import com.voxelbridge.export.util.geometry.VertexExtractor;
import com.voxelbridge.platform.client.ClientAccessHolder;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Processes individual quads and outputs them to the scene sink (PASS 2 logic).
 * Handles tint colors, PBR textures, and dynamic texture registration.
 */
public final class QuadProcessor {

    private final ExportContext ctx;
    private final Level level;
    private final IrSink sceneSink;
    private final double offsetX, offsetY, offsetZ;
    private final PlaneOffsetTracker planeOffset;

    // Placeholder to ensure tool reads the file first
    private final Set<String> pbrLoadedSprites = new HashSet<>();

    // Pending quads for per-block dedup/cull decisions.
    private final List<PendingQuad> pendingQuads = new ArrayList<>();

    private static final float CENTER_QUANT = 10000f;
    private static final float AABB_EPS = 1e-3f;
    private static final float FACE_EPS = 1e-4f;
    private static final float BOUNDARY_EPS = 5e-4f;
    private static final float NONSOLID_INSET = 5e-4f;
    private static final float NORMAL_PARALLEL_DOT = 0.999f;

    private record QuadDedupKey(int spriteHash, int tintArgb, int cx, int cy, int cz,
                                int minU, int maxU, int minV, int maxV) {}
    private record PendingQuad(QuadDedupKey key, BlockState state, BlockPos pos,
                               QuadData quad, Direction dir, String materialKey, String spriteKey,
                               float[] positions, float[] uvs, float[] normal,
                               ColorModeHandler.ColorData colorData, boolean doubleSided) {}

    public QuadProcessor(ExportContext ctx, Level level, IrSink sceneSink,
                         double offsetX, double offsetY, double offsetZ,
                         PlaneOffsetTracker planeOffset) {
        this.ctx = ctx;
        this.level = level;
        this.sceneSink = sceneSink;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.offsetZ = offsetZ;
        this.planeOffset = planeOffset;
    }

    /**
     * Clears all caches. Call this before processing each block.
     */
    public void clear() {
        pendingQuads.clear();
        // Note: pbrLoadedSprites is intentionally NOT cleared to avoid redundant loads
    }

    /**
     * Processes a single quad and outputs it to the scene sink.
     *
     * @param state block state
     * @param pos block position
     * @param quad the quad data
     * @param blockKey material key for this block
     * @param randomOffset vanilla random offset
     */
    public void processQuad(BlockState state, BlockPos pos, QuadData quad,
                            String blockKey, Vec3 randomOffset) {
        TextureAtlasSprite sprite = quad.sprite();
        if (sprite == null) return;

        String spriteKey = ctx.getTextureAccess().resolveSpriteKey(sprite);

        // Load PBR textures (once per sprite)
        if (!pbrLoadedSprites.contains(spriteKey)) {
            ensurePbrTexturesCached(sprite, spriteKey);
            pbrLoadedSprites.add(spriteKey);
        }

        // Handle dynamic textures (CTM, numbered sprites)
        boolean isDynamic = spriteKey.matches(".*\\d+$") || !ctx.getMaterialPaths().containsKey(spriteKey);
        if (isDynamic) {
            com.voxelbridge.export.texture.TextureAtlasManager.registerTint(ctx, spriteKey, 0xFFFFFF);
            if (ctx.getCachedSpriteImage(spriteKey) == null) {
                try {
                    BufferedImage img = ctx.getTextureAccess().readSprite(sprite);
                    if (img != null) ctx.cacheSpriteImage(spriteKey, img);
                } catch (Exception ignore) {}
            }
        }

        // Extract vertex data
        VertexExtractor.VertexData vertexData = VertexExtractor.extractFromQuad(
            quad, pos, sprite, offsetX, offsetY, offsetZ, randomOffset
        );

        boolean hasBaked = ColorUtil.hasBakedColors(vertexData.colors());
        boolean doubleSided = state.getBlock() instanceof BushBlock;
        boolean exportDoubleSided = ExportRuntimeConfig.isExportDoubleSidedEnabled();
        Direction dir = quad.direction();
        if (exportDoubleSided && dir != null && shouldCullSameNonSolidFace(state, pos, quad, dir)) {
            return;
        }

        ColorModeHandler.ColorData colorData;
        int tintColor = -1;
        if (!hasBaked) {
            tintColor = computeTintColor(state, pos, quad);
        }

        int dedupTint = hasBaked
            ? ColorUtil.extractBakedTintArgb(vertexData.colors())
            : (tintColor != -1 ? tintColor : 0xFFFFFFFF);

        if (hasBaked) {
            // Prefer baked vertex colors (e.g., FRAPI-provided tint) over vanilla tint.
            ColorMode mode = ctx.getColorMode();
            if (mode != null && mode.usesColormap()) {
                int bakedTint = ColorUtil.extractBakedTintArgb(vertexData.colors());
                colorData = ColorModeHandler.prepareColors(
                    ctx.getColorMode(), ctx.getColorMapAccess(), bakedTint, true);
            } else {
                float[] linearColors = ColorUtil.convertArgbToLinearRgba(vertexData.colors());
                colorData = new ColorModeHandler.ColorData(null, linearColors);
            }
        } else {
            // Compute tint color (returns -1 if no tint found)
            if (tintColor != -1) {
                // Found a valid block tint color
                colorData = ColorModeHandler.prepareColors(
                    ctx.getColorMode(), ctx.getColorMapAccess(), tintColor, true);
            } else {
                // Default to white
                colorData = ColorModeHandler.prepareColors(
                    ctx.getColorMode(), ctx.getColorMapAccess(), 0xFFFFFFFF, false);
            }
        }

        String materialKey = ctx.resolveMaterialKey(spriteKey, blockKey);
        QuadDedupKey key = buildDedupKey(spriteKey.hashCode(), dedupTint, vertexData.positions(), vertexData.normal());
        pendingQuads.add(new PendingQuad(key, state, pos, quad, dir, materialKey, spriteKey,
            vertexData.positions(), vertexData.uvs(), vertexData.normal(), colorData, doubleSided));
    }

    /**
     * Flushes cached quads after a block is fully processed.
     */
    public void flush() {
        if (pendingQuads.isEmpty()) {
            return;
        }
        boolean exportDoubleSided = ExportRuntimeConfig.isExportDoubleSidedEnabled();
        boolean nonsolidCulling = ExportRuntimeConfig.isNonsolidCullingEnabled();
        Map<QuadDedupKey, List<float[]>> normalsByKey = exportDoubleSided ? new HashMap<>() : null;

        for (PendingQuad quad : pendingQuads) {
            if (exportDoubleSided) {
                List<float[]> normals = normalsByKey.get(quad.key);
                if (normals == null) {
                    normals = new ArrayList<>(2);
                    normalsByKey.put(quad.key, normals);
                } else {
                    boolean parallel = false;
                    for (int i = 0; i < normals.size(); i++) {
                        if (areParallel(normals.get(i), quad.normal)) {
                            parallel = true;
                            break;
                        }
                    }
                    if (parallel) {
                        continue; // drop one of parallel faces
                    }
                }
                normals.add(normalize(quad.normal));
            }
            if (shouldCullAgainstSolid(quad.state, quad.pos, quad.quad, quad.dir)) {
                if (nonsolidCulling) {
                    continue;
                }
                applyInsetAgainstSolid(quad.dir, quad.positions);
            }
            applyInsetAgainstNonSolid(quad.state, quad.pos, quad.quad, quad.dir, quad.positions);
            ctx.registerSpriteMaterial(quad.spriteKey, quad.materialKey);
            if (planeOffset != null) {
                Direction offsetDir = quad.dir != null ? quad.dir : inferOutwardDirection(quad.positions, quad.pos);
                planeOffset.applyOffset(quad.positions, quad.normal, offsetDir);
            }
            TintMode tintMode = ctx.getColorMode() != null && ctx.getColorMode().usesColormap()
                ? TintMode.COLORMAP
                : TintMode.VERTEX_COLOR;
            sceneSink.addQuad(ctx.intern(quad.materialKey), ctx.intern(quad.spriteKey), null,
                RenderLayer.UNKNOWN, tintMode, quad.doubleSided, false,
                quad.positions, quad.uvs, quad.colorData.uv1(), quad.normal, quad.colorData.colors());
        }
        pendingQuads.clear();
    }

    /**
     * Computes tint color from block colors. Returns -1 if no tint logic exists.
     */
    private int computeTintColor(BlockState state, BlockPos pos, QuadData quad) {
        int tintIndex = quad.tintIndex();
        if (tintIndex < 0) return -1;
        return ClientAccessHolder.get().getMinecraft().getBlockColors().getColor(state, level, pos, tintIndex);
    }

    /**
     * Pre-loads PBR companion textures (normal and specular maps).
     */
    private void ensurePbrTexturesCached(TextureAtlasSprite sprite, String spriteKey) {
        if (sprite == null || spriteKey == null) return;
        // Use PbrTextureHelper's enhanced lookup logic
        com.voxelbridge.export.texture.PbrTextureHelper.ensurePbrCached(ctx, spriteKey, sprite);
    }

    private boolean shouldCullSameNonSolidFace(Direction dir) {
        return dir == Direction.EAST || dir == Direction.SOUTH || dir == Direction.UP;
    }

    private boolean shouldCullSameNonSolidFace(BlockState state, BlockPos pos, QuadData quad, Direction dir) {
        if (!shouldCullSameNonSolidFace(dir)) {
            return false;
        }
        BlockPos neighbor = pos.relative(dir);
        BlockState neighborState = level.getBlockState(neighbor);
        if (neighborState.getBlock() != state.getBlock()) {
            return false;
        }
        if (BlockStateCompat.isSolidRender(neighborState, level, neighbor)) {
            return false;
        }
        float[] quadAabb = new float[4];
        if (!getLocalFaceAabb(quad, dir, quadAabb)) {
            return false;
        }
        float[] neighborAabb = new float[4];
        if (!getNeighborFaceAabb(neighborState, neighbor, dir.getOpposite(), neighborAabb)) {
            return false;
        }
        return aabbApproxEqual(quadAabb, neighborAabb);
    }

    private QuadDedupKey buildDedupKey(int spriteHash, int tintArgb, float[] positions, float[] normal) {
        float cx = (positions[0] + positions[3] + positions[6] + positions[9]) * 0.25f;
        float cy = (positions[1] + positions[4] + positions[7] + positions[10]) * 0.25f;
        float cz = (positions[2] + positions[5] + positions[8] + positions[11]) * 0.25f;

        int qx = Math.round(cx * CENTER_QUANT);
        int qy = Math.round(cy * CENTER_QUANT);
        int qz = Math.round(cz * CENTER_QUANT);

        float[] n = normalize(normal);
        float[] aabb2d = projectAabb2d(positions, n);
        int minU = Math.round(aabb2d[0] * 1000f);
        int maxU = Math.round(aabb2d[1] * 1000f);
        int minV = Math.round(aabb2d[2] * 1000f);
        int maxV = Math.round(aabb2d[3] * 1000f);
        return new QuadDedupKey(spriteHash, tintArgb, qx, qy, qz, minU, maxU, minV, maxV);
    }

    private static float[] normalize(float[] n) {
        float lenSq = n[0] * n[0] + n[1] * n[1] + n[2] * n[2];
        if (lenSq < 1e-8f) {
            return new float[]{0f, 1f, 0f};
        }
        float inv = 1f / (float) Math.sqrt(lenSq);
        return new float[]{n[0] * inv, n[1] * inv, n[2] * inv};
    }

    private static boolean areParallel(float[] a, float[] b) {
        float ax = a[0], ay = a[1], az = a[2];
        float bx = b[0], by = b[1], bz = b[2];
        float lenA = ax * ax + ay * ay + az * az;
        float lenB = bx * bx + by * by + bz * bz;
        if (lenA < 1e-8f || lenB < 1e-8f) {
            return false;
        }
        float invA = 1f / (float) Math.sqrt(lenA);
        float invB = 1f / (float) Math.sqrt(lenB);
        float dot = (ax * invA) * (bx * invB) + (ay * invA) * (by * invB) + (az * invA) * (bz * invB);
        return Math.abs(dot) >= NORMAL_PARALLEL_DOT;
    }

    private static float[] projectAabb2d(float[] positions, float[] normal) {
        float anx = Math.abs(normal[0]);
        float any = Math.abs(normal[1]);
        float anz = Math.abs(normal[2]);
        int axis;
        if (anx >= any && anx >= anz) {
            axis = 0;
        } else if (any >= anz) {
            axis = 1;
        } else {
            axis = 2;
        }

        float minU = Float.POSITIVE_INFINITY;
        float maxU = Float.NEGATIVE_INFINITY;
        float minV = Float.POSITIVE_INFINITY;
        float maxV = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < 4; i++) {
            float x = positions[i * 3];
            float y = positions[i * 3 + 1];
            float z = positions[i * 3 + 2];
            float u;
            float v;
            if (axis == 0) {
                u = y;
                v = z;
            } else if (axis == 1) {
                u = x;
                v = z;
            } else {
                u = x;
                v = y;
            }
            if (u < minU) minU = u;
            if (u > maxU) maxU = u;
            if (v < minV) minV = v;
            if (v > maxV) maxV = v;
        }
        return new float[]{minU, maxU, minV, maxV};
    }

    private static boolean aabbApproxEqual(float[] a, float[] b) {
        return Math.abs(a[0] - b[0]) <= AABB_EPS
            && Math.abs(a[1] - b[1]) <= AABB_EPS
            && Math.abs(a[2] - b[2]) <= AABB_EPS
            && Math.abs(a[3] - b[3]) <= AABB_EPS;
    }

    private boolean shouldCullAgainstSolid(BlockState state, BlockPos pos, QuadData quad, Direction dir) {
        if (state == null || quad == null || dir == null) {
            return false;
        }
        if (BlockStateCompat.isSolidRender(state, level, pos)) {
            return false;
        }
        BlockPos neighborPos = pos.relative(dir);
        BlockState neighborState = level.getBlockState(neighborPos);
        if (!BlockStateCompat.isSolidRender(neighborState, level, neighborPos)) {
            return false;
        }
        float[] quadAabb = new float[4];
        if (!getLocalFaceAabb(quad, dir, quadAabb)) {
            return false;
        }
        float[] neighborAabb = new float[4];
        if (!getNeighborFaceAabb(neighborState, neighborPos, dir.getOpposite(), neighborAabb)) {
            return false;
        }
        return aabbContains(neighborAabb, quadAabb);
    }

    private static boolean aabbContains(float[] container, float[] inner) {
        return container[0] - AABB_EPS <= inner[0]
            && container[1] + AABB_EPS >= inner[1]
            && container[2] - AABB_EPS <= inner[2]
            && container[3] + AABB_EPS >= inner[3];
    }

    private void applyInsetAgainstNonSolid(BlockState state, BlockPos pos, QuadData quad, Direction dir, float[] positions) {
        if (state == null || quad == null || dir == null || positions == null || positions.length < 12) {
            return;
        }
        if (BlockStateCompat.isSolidRender(state, level, pos)) {
            return;
        }
        BlockPos neighborPos = pos.relative(dir);
        BlockState neighborState = level.getBlockState(neighborPos);
        if (BlockStateCompat.isSolidRender(neighborState, level, neighborPos)) {
            return;
        }
        if (neighborState.getBlock() == state.getBlock()) {
            return;
        }
        if (!isBoundaryFaceLocal(quad, dir)) {
            return;
        }
        float[] quadAabb = new float[4];
        if (!getLocalFaceAabb(quad, dir, quadAabb)) {
            return;
        }
        float[] neighborAabb = new float[4];
        if (!getNeighborFaceAabb(neighborState, neighborPos, dir.getOpposite(), neighborAabb)) {
            return;
        }
        if (!aabbOverlaps(quadAabb, neighborAabb)) {
            return;
        }
        String selfKey = String.valueOf(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
        String otherKey = String.valueOf(BuiltInRegistries.BLOCK.getKey(neighborState.getBlock()));
        if (selfKey.compareTo(otherKey) <= 0) {
            return;
        }
        float dx = -dir.getStepX() * NONSOLID_INSET;
        float dy = -dir.getStepY() * NONSOLID_INSET;
        float dz = -dir.getStepZ() * NONSOLID_INSET;
        for (int i = 0; i < 4; i++) {
            positions[i * 3] += dx;
            positions[i * 3 + 1] += dy;
            positions[i * 3 + 2] += dz;
        }
    }

    private void applyInsetAgainstSolid(Direction dir, float[] positions) {
        if (dir == null || positions == null || positions.length < 12) {
            return;
        }
        float dx = -dir.getStepX() * NONSOLID_INSET;
        float dy = -dir.getStepY() * NONSOLID_INSET;
        float dz = -dir.getStepZ() * NONSOLID_INSET;
        for (int i = 0; i < 4; i++) {
            positions[i * 3] += dx;
            positions[i * 3 + 1] += dy;
            positions[i * 3 + 2] += dz;
        }
    }

    private static boolean getLocalFaceAabb(QuadData quad, Direction dir, float[] out) {
        int[] verts = quad.vertices();
        if (verts == null || verts.length < 32 || out == null || out.length < 4) {
            return false;
        }
        Direction.Axis axis = dir.getAxis();
        float minU = Float.POSITIVE_INFINITY;
        float maxU = Float.NEGATIVE_INFINITY;
        float minV = Float.POSITIVE_INFINITY;
        float maxV = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < 4; i++) {
            int base = i * 8;
            float x = Float.intBitsToFloat(verts[base]);
            float y = Float.intBitsToFloat(verts[base + 1]);
            float z = Float.intBitsToFloat(verts[base + 2]);
            float u;
            float v;
            if (axis == Direction.Axis.X) {
                u = y;
                v = z;
            } else if (axis == Direction.Axis.Y) {
                u = x;
                v = z;
            } else {
                u = x;
                v = y;
            }
            if (u < minU) minU = u;
            if (u > maxU) maxU = u;
            if (v < minV) minV = v;
            if (v > maxV) maxV = v;
        }
        out[0] = minU;
        out[1] = maxU;
        out[2] = minV;
        out[3] = maxV;
        return true;
    }

    private static boolean isBoundaryFaceLocal(QuadData quad, Direction dir) {
        int[] verts = quad.vertices();
        if (verts == null || verts.length < 32) {
            return false;
        }
        Direction.Axis axis = dir.getAxis();
        float target = dir.getAxisDirection() == Direction.AxisDirection.NEGATIVE ? 0f : 1f;
        for (int i = 0; i < 4; i++) {
            int base = i * 8;
            float v;
            if (axis == Direction.Axis.X) {
                v = Float.intBitsToFloat(verts[base]);
            } else if (axis == Direction.Axis.Y) {
                v = Float.intBitsToFloat(verts[base + 1]);
            } else {
                v = Float.intBitsToFloat(verts[base + 2]);
            }
            if (Math.abs(v - target) > BOUNDARY_EPS) {
                return false;
            }
        }
        return true;
    }

    private static boolean aabbOverlaps(float[] a, float[] b) {
        return a[1] + AABB_EPS > b[0]
            && a[0] - AABB_EPS < b[1]
            && a[3] + AABB_EPS > b[2]
            && a[2] - AABB_EPS < b[3];
    }

    private Direction inferOutwardDirection(float[] positions, BlockPos pos) {
        if (positions == null || positions.length < 12 || pos == null) {
            return null;
        }
        float cx = (positions[0] + positions[3] + positions[6] + positions[9]) * 0.25f;
        float cy = (positions[1] + positions[4] + positions[7] + positions[10]) * 0.25f;
        float cz = (positions[2] + positions[5] + positions[8] + positions[11]) * 0.25f;

        float bx = (float) (pos.getX() + 0.5 + offsetX);
        float by = (float) (pos.getY() + 0.5 + offsetY);
        float bz = (float) (pos.getZ() + 0.5 + offsetZ);

        float dx = cx - bx;
        float dy = cy - by;
        float dz = cz - bz;

        float adx = Math.abs(dx);
        float ady = Math.abs(dy);
        float adz = Math.abs(dz);

        if (adx >= ady && adx >= adz) {
            return dx >= 0f ? Direction.EAST : Direction.WEST;
        }
        if (ady >= adx && ady >= adz) {
            return dy >= 0f ? Direction.UP : Direction.DOWN;
        }
        return dz >= 0f ? Direction.SOUTH : Direction.NORTH;
    }

    private boolean getNeighborFaceAabb(BlockState state, BlockPos pos, Direction face, float[] out) {
        if (out == null || out.length < 4) {
            return false;
        }
        VoxelShape shape = state.getShape(level, pos);
        if (shape.isEmpty()) {
            return false;
        }
        boolean found = false;
        float minU = Float.POSITIVE_INFINITY;
        float maxU = Float.NEGATIVE_INFINITY;
        float minV = Float.POSITIVE_INFINITY;
        float maxV = Float.NEGATIVE_INFINITY;
        Direction.Axis axis = face.getAxis();
        boolean negative = face.getAxisDirection() == Direction.AxisDirection.NEGATIVE;

        for (AABB box : shape.toAabbs()) {
            if (axis == Direction.Axis.X) {
                if (negative) {
                    if (box.minX > FACE_EPS) continue;
                } else if (box.maxX < 1.0 - FACE_EPS) {
                    continue;
                }
                minU = Math.min(minU, (float) box.minY);
                maxU = Math.max(maxU, (float) box.maxY);
                minV = Math.min(minV, (float) box.minZ);
                maxV = Math.max(maxV, (float) box.maxZ);
            } else if (axis == Direction.Axis.Y) {
                if (negative) {
                    if (box.minY > FACE_EPS) continue;
                } else if (box.maxY < 1.0 - FACE_EPS) {
                    continue;
                }
                minU = Math.min(minU, (float) box.minX);
                maxU = Math.max(maxU, (float) box.maxX);
                minV = Math.min(minV, (float) box.minZ);
                maxV = Math.max(maxV, (float) box.maxZ);
            } else {
                if (negative) {
                    if (box.minZ > FACE_EPS) continue;
                } else if (box.maxZ < 1.0 - FACE_EPS) {
                    continue;
                }
                minU = Math.min(minU, (float) box.minX);
                maxU = Math.max(maxU, (float) box.maxX);
                minV = Math.min(minV, (float) box.minY);
                maxV = Math.max(maxV, (float) box.maxY);
            }
            found = true;
        }

        if (!found) {
            return false;
        }
        out[0] = minU;
        out[1] = maxU;
        out[2] = minV;
        out[3] = maxV;
        return true;
    }

}


