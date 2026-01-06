package com.voxelbridge.export.exporter.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.voxelbridge.core.ir.IrSink;
import com.voxelbridge.core.ir.RenderLayer;
import com.voxelbridge.export.ExportContext;
import com.voxelbridge.platform.render.capture.CaptureBufferBase;
import com.voxelbridge.platform.render.capture.RenderCapture;
import com.voxelbridge.platform.render.capture.RenderCaptureUtil;
import com.voxelbridge.export.exporter.resolve.AtlasLocator;
import com.voxelbridge.export.exporter.resolve.RenderTypeResolver;
import com.voxelbridge.export.exporter.resolve.ResolvedTexture;
import com.voxelbridge.export.exporter.resolve.TextureResolver;
import com.voxelbridge.platform.render.RenderTypeTextureResolver;
import com.voxelbridge.util.debug.LogModule;
import com.voxelbridge.util.debug.VoxelBridgeLogger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.List;

/**
 * Renders BlockEntities and captures their geometry to an IR sink.
 * This replaces the old BerRenderHelper + BerCaptureBuffer system.
 */
@OnlyIn(Dist.CLIENT)
public final class BlockEntityRenderer {

    private static final float[] EMPTY_UV = new float[8];
    private static final float[] NORMAL_UP = new float[] {
        0f, 1f, 0f,
        0f, 1f, 0f,
        0f, 1f, 0f,
        0f, 1f, 0f
    };
    private static AtlasLocator ATLAS_LOCATOR = new BlockEntityAtlasLocator(Minecraft.getInstance());
    private static final ThreadLocal<TextureOverrideMap> OVERRIDES = new ThreadLocal<>();
    private static TextureResolver<BlockEntity> TEXTURE_RESOLVER = BlockEntityTextureResolver.INSTANCE;
    private static RenderTypeResolver RENDER_TYPE_RESOLVER = RenderTypeTextureResolver.INSTANCE;

    private BlockEntityRenderer() {}

    public static void setAtlasLocator(AtlasLocator locator) {
        if (locator != null) {
            ATLAS_LOCATOR = locator;
        }
    }

    public static void setTextureResolver(TextureResolver<BlockEntity> resolver) {
        if (resolver != null) {
            TEXTURE_RESOLVER = resolver;
        }
    }

    public static void setRenderTypeResolver(RenderTypeResolver resolver) {
        if (resolver != null) {
            RENDER_TYPE_RESOLVER = resolver;
        }
    }

    /**
     * Renders a BlockEntity and outputs geometry to the scene sink.
     *
     * @param ctx Export context
     * @param blockEntity The block entity to render
     * @param sceneSink Output sink for geometry
     * @param offsetX X offset for positioning
     * @param offsetY Y offset for positioning
     * @param offsetZ Z offset for positioning
     * @return true if the BlockEntity was successfully rendered
     */
    public static boolean render(
        ExportContext ctx,
        BlockEntity blockEntity,
        IrSink sceneSink,
        double offsetX,
        double offsetY,
        double offsetZ
    ) {
        return render(ctx, blockEntity, sceneSink, offsetX, offsetY, offsetZ, null);
    }

    public static boolean render(
        ExportContext ctx,
        BlockEntity blockEntity,
        IrSink sceneSink,
        double offsetX,
        double offsetY,
        double offsetZ,
        TextureOverrideMap overrides
    ) {
        RenderTask task = createTask(ctx, blockEntity, sceneSink, offsetX, offsetY, offsetZ, overrides);
        if (task == null) {
            return false;
        }
        ctx.runOnMainThread(task);
        return task.wasSuccessful();
    }

    /**
     * Creates a render task that can be executed later (ideally on the main thread).
     */
    public static RenderTask createTask(
        ExportContext ctx,
        BlockEntity blockEntity,
        IrSink sceneSink,
        double offsetX,
        double offsetY,
        double offsetZ,
        TextureOverrideMap overrides
    ) {
        com.voxelbridge.util.debug.VoxelBridgeLogger.debug(LogModule.BLOCKENTITY, "[BlockEntityRenderer] Attempting to render BlockEntity: " + blockEntity.getClass().getSimpleName() + " at " + blockEntity.getBlockPos());
        BlockEntityRenderDispatcher dispatcher = ctx.getMc().getBlockEntityRenderDispatcher();
        net.minecraft.client.renderer.blockentity.BlockEntityRenderer<BlockEntity> renderer =
                dispatcher.getRenderer(blockEntity);

        if (renderer == null) {
            com.voxelbridge.util.debug.VoxelBridgeLogger.debug(LogModule.BLOCKENTITY, "[BlockEntityRenderer] No renderer found for: " + blockEntity.getClass().getSimpleName());
            return null;
        }

        com.voxelbridge.util.debug.VoxelBridgeLogger.debug(LogModule.BLOCKENTITY, "[BlockEntityRenderer] Found renderer: " + renderer.getClass().getSimpleName());
        BlockPos pos = blockEntity.getBlockPos();
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        return new RenderTask(ctx, blockEntity, sceneSink, offsetX, offsetY, offsetZ, overrides, renderer, chunkX, chunkZ);
    }

    /**
     * Executes render logic without scheduling. Caller must run on the main thread.
     */
    private static boolean renderDirect(
        ExportContext ctx,
        BlockEntity blockEntity,
        IrSink sceneSink,
        double offsetX,
        double offsetY,
        double offsetZ,
        TextureOverrideMap overrides,
        net.minecraft.client.renderer.blockentity.BlockEntityRenderer<BlockEntity> renderer
    ) {
        try {
            com.voxelbridge.util.debug.VoxelBridgeLogger.debug(LogModule.BLOCKENTITY, "[BlockEntityRenderer][renderDirect] Starting render for " + blockEntity.getClass().getSimpleName());
            if (overrides != null) {
                OVERRIDES.set(overrides);
            }
            PoseStack poseStack = new PoseStack();
            poseStack.translate(offsetX, offsetY, offsetZ);

            CaptureBuffer captureBuffer = new CaptureBuffer(ctx, sceneSink, offsetX, offsetY, offsetZ, blockEntity);

            com.voxelbridge.util.debug.VoxelBridgeLogger.debug(LogModule.BLOCKENTITY, "[BlockEntityRenderer][renderDirect] Calling renderer.render()...");
            renderer.render(
                blockEntity,
                0.0f,
                poseStack,
                captureBuffer,
                0xF000F0,
                OverlayTexture.NO_OVERLAY
            );

            com.voxelbridge.util.debug.VoxelBridgeLogger.debug(LogModule.BLOCKENTITY, "[BlockEntityRenderer][renderDirect] renderer.render() returned, flushing buffer...");
            captureBuffer.flush();

            boolean hadGeometry = captureBuffer.hadGeometry();
            com.voxelbridge.util.debug.VoxelBridgeLogger.debug(LogModule.BLOCKENTITY, "[BlockEntityRenderer] Render complete: hadGeometry=" + hadGeometry);
            com.voxelbridge.util.debug.VoxelBridgeLogger.debug(LogModule.BLOCKENTITY, "[BlockEntityRenderer] Final result: " + hadGeometry);
            return hadGeometry;
        } catch (Exception e) {
            com.voxelbridge.util.debug.VoxelBridgeLogger.debug(LogModule.BLOCKENTITY, "[BlockEntityRenderer] Render error: " + e.getMessage());
            e.printStackTrace();
            com.voxelbridge.util.debug.VoxelBridgeLogger.debug(LogModule.BLOCKENTITY, "[BlockEntityRenderer] Final result: false");
            return false;
        } finally {
            OVERRIDES.remove();
        }
    }

    /**
     * Task wrapper for batch execution.
     */
    public static final class RenderTask implements Runnable {
        private final ExportContext ctx;
        private final BlockEntity blockEntity;
        private final IrSink sceneSink;
        private final double offsetX;
        private final double offsetY;
        private final double offsetZ;
        private final TextureOverrideMap overrides;
        private final net.minecraft.client.renderer.blockentity.BlockEntityRenderer<BlockEntity> renderer;
        private final int chunkX;
        private final int chunkZ;
        private boolean success;

        RenderTask(
            ExportContext ctx,
            BlockEntity blockEntity,
            IrSink sceneSink,
            double offsetX,
            double offsetY,
            double offsetZ,
            TextureOverrideMap overrides,
            net.minecraft.client.renderer.blockentity.BlockEntityRenderer<BlockEntity> renderer,
            int chunkX,
            int chunkZ
        ) {
            this.ctx = ctx;
            this.blockEntity = blockEntity;
            this.sceneSink = sceneSink;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.offsetZ = offsetZ;
            this.overrides = overrides;
            this.renderer = renderer;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
        }

        @Override
        public void run() {
            sceneSink.onChunkStart(chunkX, chunkZ);
            try {
                this.success = renderDirect(ctx, blockEntity, sceneSink, offsetX, offsetY, offsetZ, overrides, renderer);
            } finally {
                sceneSink.onChunkEnd(chunkX, chunkZ, this.success);
            }
        }

        public boolean wasSuccessful() {
            return success;
        }
    }

    /**
     * Captures rendered geometry from BlockEntity renderers.
     */
    private static class CaptureBuffer extends CaptureBufferBase {
        private final double offsetX, offsetY, offsetZ;
        private final BlockEntity blockEntity;
        private final TextureOverrideMap overrides;

        CaptureBuffer(ExportContext ctx, IrSink sceneSink, double offsetX, double offsetY, double offsetZ, BlockEntity blockEntity) {
            super(ctx, sceneSink, (renderType, queuedVertices) -> {
                if (VoxelBridgeLogger.isDebugEnabled(LogModule.BLOCKENTITY)) {
                    VoxelBridgeLogger.debug(LogModule.BLOCKENTITY, "[VertexCollector] setNormal called, vertices.size=" + queuedVertices);
                }
            });
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.offsetZ = offsetZ;
            this.blockEntity = blockEntity;
            this.overrides = OVERRIDES.get();
        }

        void flush() {
            flushCapture();
        }

        TextureOverrideMap overrides() {
            return overrides;
        }

        @Override
        public void onQuad(RenderType renderType, List<RenderCapture.Vertex> verts) {
            if (verts.size() < 3) return;

            boolean logQuads = VoxelBridgeLogger.isDebugEnabled(LogModule.BLOCKENTITY);
            if (logQuads) {
                VoxelBridgeLogger.debug(LogModule.BLOCKENTITY, "[VertexCollector] ========== OUTPUT QUAD START ==========");
                VoxelBridgeLogger.debug(LogModule.BLOCKENTITY, "[VertexCollector] Vertices count: " + verts.size());
            }

            // Check for degenerate quad (zero or near-zero area)
            if (verts.size() >= 3) {
                float area = computeQuadArea(verts);
                if (area < 0.0001f) {
                    if (logQuads) {
                        VoxelBridgeLogger.debug(LogModule.BLOCKENTITY, "[VertexCollector] Skipping degenerate quad (area=" + area + ")");
                    }
                    return;
                }
            }

            recordGeometry();

            // Build position and color arrays (UV will be filled later after texture resolution)
            float[] positions = new float[12];
            float[] uv0 = new float[8];
            float[] colors = new float[16];

            for (int i = 0; i < Math.min(4, verts.size()); i++) {
                RenderCapture.Vertex v = verts.get(i);
                positions[i * 3] = v.x;
                positions[i * 3 + 1] = v.y;
                positions[i * 3 + 2] = v.z;

                // Extract RGBA from packed color
                colors[i * 4] = ((v.color >> 16) & 0xFF) / 255.0f;  // R
                colors[i * 4 + 1] = ((v.color >> 8) & 0xFF) / 255.0f;   // G
                colors[i * 4 + 2] = (v.color & 0xFF) / 255.0f;          // B
                colors[i * 4 + 3] = ((v.color >> 24) & 0xFF) / 255.0f;  // A
            }

            // Extract texture from RenderType and register it
            ResolvedTexture textureRes = TEXTURE_RESOLVER.resolve(blockEntity, renderType);
            TextureOverrideMap overrides = overrides();
            String spriteKey;
            boolean isAtlasTexture = false;
            float u0 = 0f, u1 = 1f, v0 = 0f, v1 = 1f;
            ResourceLocation atlasLocation = textureRes != null ? textureRes.atlasLocation() : null;

            int vertCount = Math.min(4, verts.size());
            RenderCaptureUtil.UvStats uvStats = RenderCaptureUtil.computeUvStats(verts);

            // Handle atlas texture resolution via locator if needed
            if (textureRes != null && textureRes.isAtlasTexture() && textureRes.sprite() == null) {
                textureRes = RenderCaptureUtil.resolveAtlasSprite(textureRes, ATLAS_LOCATOR, uvStats, atlasLocation);
                if (textureRes != null) {
                    atlasLocation = textureRes.atlasLocation();
                }
            }

            // Generate Material Group Key
            // Format: "blockentity:minecraft:chest"
            String materialGroupKey = "blockentity:" + net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE
                .getKey(blockEntity.getType()).toString();

            if (textureRes != null && overrides != null) {
                if (overrides.skipQuad(textureRes.texture(), uvStats.rawU(), uvStats.rawV())) return;

                var mappedHandle = overrides.resolve(textureRes.texture());
                if (mappedHandle != null) {
                    spriteKey = mappedHandle.spriteKey();

                    // Use the resolved atlas bounds so fillUvs() can denormalize atlas-space UVs correctly
                    isAtlasTexture = textureRes.isAtlasTexture();
                    u0 = textureRes.u0(); u1 = textureRes.u1();
                    v0 = textureRes.v0(); v1 = textureRes.v1();

                    // IMPORTANT: Keep original isAtlasTexture and bounds to allow fillUvs()
                    // to correctly denormalize atlas-space UVs to sprite-space.
                    // Do NOT force isAtlasTexture=false here, as some overrides (e.g., Banner)
                    // may still have UVs in atlas-space that need denormalization.
                    // (keep original isAtlasTexture, u0, u1, v0, v1 values from textureRes)

                    fillUvs(verts, uv0, isAtlasTexture, u0, u1, v0, v1);

                    RenderCaptureUtil.ColorModeResult colorResult =
                        RenderCaptureUtil.applyColorMode(ctx, colors, EMPTY_UV);
                    sceneSink.addQuad(materialGroupKey, spriteKey, "voxelbridge:transparent",
                        RenderLayer.UNKNOWN, colorResult.tintMode(),
                        RENDER_TYPE_RESOLVER.isDoubleSided(renderType),
                        false,
                        positions, uv0, colorResult.uv1(), NORMAL_UP, colors);
                    return;
                }
            }

            if (textureRes != null && textureRes.texture() != null) {
                spriteKey = com.voxelbridge.export.texture.BlockEntityTextureManager.registerTexture(ctx, textureRes);
                isAtlasTexture = textureRes.isAtlasTexture();
                u0 = textureRes.u0(); u1 = textureRes.u1();
                v0 = textureRes.v0(); v1 = textureRes.v1();
            } else {
                spriteKey = "blockentity:minecraft/block/white";
            }

            // Heuristic: if renderer passed sprite-space UVs (0..1) but texture came from an atlas,
            // switch to sprite-space normalization to avoid sampling the whole atlas.
            if (isAtlasTexture && textureRes != null && textureRes.sprite() != null) {
                float eps = 1e-4f;
                boolean outsideSpriteBounds =
                    uvStats.minU() < textureRes.u0() - eps || uvStats.maxU() > textureRes.u1() + eps ||
                    uvStats.minV() < textureRes.v0() - eps || uvStats.maxV() > textureRes.v1() + eps;
                if (outsideSpriteBounds) {
                    isAtlasTexture = false;
                    u0 = 0f; u1 = 1f;
                    v0 = 0f; v1 = 1f;
                }
            }

            fillUvs(verts, uv0, isAtlasTexture, u0, u1, v0, v1);

            // NOTE: UV remapping to atlas space is handled by GltfSceneBuilder.remapUV()
            // to avoid double transformation. Do NOT remap UVs here.
            // Keep UVs in sprite space (0-1) for now, consistent with BlockExporter and FluidExporter.

            // Color/colormap handling to mirror BlockExporter behavior:
            // - ColorMap mode: always provide TEXCOORD_1; no tint -> white slot.
            // - VertexColor mode: keep per-vertex colors (already in 'colors').
            RenderCaptureUtil.ColorModeResult colorResult =
                RenderCaptureUtil.applyColorMode(ctx, colors, EMPTY_UV);

            // Send quad to scene sink using the BlockEntity type as group key (block entities typically don't have overlays)
            ctx.registerSpriteMaterial(spriteKey, materialGroupKey);
            sceneSink.addQuad(materialGroupKey, spriteKey, "voxelbridge:transparent",
                RenderLayer.UNKNOWN, colorResult.tintMode(),
                RENDER_TYPE_RESOLVER.isDoubleSided(renderType),
                false,
                positions, uv0, colorResult.uv1(), NORMAL_UP, colors);
        }

        private void fillUvs(List<RenderCapture.Vertex> verts, float[] uv0, boolean isAtlas, float u0, float u1, float v0, float v1) {
            int count = Math.min(4, verts.size());
            if (isAtlas) {
                RenderCaptureUtil.fillUvsAtlas(verts, uv0, u0, u1, v0, v1);
            } else {
                RenderCaptureUtil.fillUvsClamp(verts, uv0);
            }
        }

        private float computeQuadArea(List<RenderCapture.Vertex> verts) {
            if (verts.size() < 3) return 0f;
            RenderCapture.Vertex v0 = verts.get(0);
            RenderCapture.Vertex v1 = verts.get(1);
            RenderCapture.Vertex v2 = verts.get(2);
            float ax = v1.x - v0.x; float ay = v1.y - v0.y; float az = v1.z - v0.z;
            float bx = v2.x - v0.x; float by = v2.y - v0.y; float bz = v2.z - v0.z;
            float cx = ay * bz - az * by;
            float cy = az * bx - ax * bz;
            float cz = ax * by - ay * bx;
            return (float) Math.sqrt(cx * cx + cy * cy + cz * cz);
        }
    }

}




