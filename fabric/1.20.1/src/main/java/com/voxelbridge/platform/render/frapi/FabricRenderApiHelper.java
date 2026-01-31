package com.voxelbridge.platform.render.frapi;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.fabricmc.fabric.api.renderer.v1.Renderer;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadView;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel;
import net.fabricmc.fabric.api.renderer.v1.model.SpriteFinder;
import net.fabricmc.fabric.impl.client.indigo.renderer.aocalc.AoCalculator;
import net.fabricmc.fabric.impl.client.indigo.renderer.aocalc.AoLuminanceFix;
import net.fabricmc.fabric.impl.client.indigo.renderer.mesh.MutableQuadViewImpl;
import net.fabricmc.fabric.impl.client.indigo.renderer.render.AbstractBlockRenderContext;
import net.fabricmc.fabric.impl.client.indigo.renderer.render.BlockRenderInfo;
import com.voxelbridge.mixin.BlockRenderInfoAccessor;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.RenderType;

import java.util.ArrayList;
import java.util.List;
import com.voxelbridge.util.debug.LogModule;
import com.voxelbridge.util.debug.VoxelBridgeLogger;

/**
 * Fabric Rendering API helper for extracting quads from FabricBakedModel.
 */
public final class FabricRenderApiHelper {
    private FabricRenderApiHelper() {}

    public static List<BakedQuad> extractQuads(
        FabricBakedModel model,
        BlockAndTintGetter level,
        BlockState state,
        BlockPos pos,
        RandomSource rand,
        long seed,
        SpriteFinder spriteFinder
    ) {
        try {
            Renderer renderer = RendererAccess.INSTANCE.getRenderer();
            if (renderer == null) {
                if (VoxelBridgeLogger.isDebugEnabled(LogModule.EXPORT)) {
                    VoxelBridgeLogger.debug(LogModule.EXPORT, "[FRAPI] RendererAccess returned null");
                }
                return new ArrayList<>();
            }

            List<BakedQuad> fabricQuads = new ArrayList<>();
            IndigoCaptureContext indigoContext = new IndigoCaptureContext(spriteFinder, fabricQuads);
            indigoContext.emitQuads(level, state, pos, model, rand, seed);
            return fabricQuads;
        } catch (Throwable t) {
            VoxelBridgeLogger.error(LogModule.EXPORT, "[FRAPI] extractQuads failed: " + t.getClass().getName() + ": " + t.getMessage(), t);
            return new ArrayList<>();
        }
    }

    private static BakedQuad toBakedQuad(QuadView quad, SpriteFinder spriteFinder) {
        int vertexSize = DefaultVertexFormat.BLOCK.getVertexSize() / 4;
        int[] vertices = new int[vertexSize * 4];

        for (int i = 0; i < 4; i++) {
            int offset = i * vertexSize;
            vertices[offset] = Float.floatToRawIntBits(quad.x(i));
            vertices[offset + 1] = Float.floatToRawIntBits(quad.y(i));
            vertices[offset + 2] = Float.floatToRawIntBits(quad.z(i));
            vertices[offset + 3] = quad.color(i);
            vertices[offset + 4] = Float.floatToRawIntBits(quad.u(i));
            vertices[offset + 5] = Float.floatToRawIntBits(quad.v(i));
            vertices[offset + 6] = quad.lightmap(i);

            if (quad.hasNormal(i)) {
                float nx = quad.normalX(i);
                float ny = quad.normalY(i);
                float nz = quad.normalZ(i);
                vertices[offset + 7] = packNormal(nx, ny, nz);
            } else {
                Direction dir = quad.lightFace();
                if (dir != null) {
                    vertices[offset + 7] = packNormal(dir.getStepX(), dir.getStepY(), dir.getStepZ());
                } else {
                    vertices[offset + 7] = packNormal(0, 1, 0);
                }
            }
        }

        TextureAtlasSprite sprite = spriteFinder.find(quad, 0);
        Direction cullFace = quad.cullFace();
        int tintIndex = quad.colorIndex();
        boolean shade = true;

        return new BakedQuad(vertices, tintIndex, cullFace, sprite, shade);
    }

    private static int packNormal(float x, float y, float z) {
        float len = (float) Math.sqrt(x * x + y * y + z * z);
        if (len > 0.0001f) {
            x /= len;
            y /= len;
            z /= len;
        }
        int nx = (int) (x * 127.0f) & 0xFF;
        int ny = (int) (y * 127.0f) & 0xFF;
        int nz = (int) (z * 127.0f) & 0xFF;
        return nx | (ny << 8) | (nz << 16);
    }

    private static final class IndigoCaptureContext extends AbstractBlockRenderContext {
        private final SpriteFinder spriteFinder;
        private final List<BakedQuad> out;

        private IndigoCaptureContext(SpriteFinder spriteFinder, List<BakedQuad> out) {
            this.spriteFinder = spriteFinder;
            this.out = out;
        }

        void emitQuads(
            BlockAndTintGetter level,
            BlockState state,
            BlockPos pos,
            FabricBakedModel model,
            RandomSource rand,
            long seed
        ) {
            try {
                BlockRenderInfoAccessor accessor = (BlockRenderInfoAccessor) (Object) blockInfo;
                accessor.voxelbridge$setRandom(rand);
                accessor.voxelbridge$setSeed(seed);
                accessor.voxelbridge$setRecomputeSeed(false);

                aoCalc.clear();
                blockInfo.prepareForWorld(level, false);
                blockInfo.prepareForBlock(state, pos, ((BakedModel) model).useAmbientOcclusion());
                accessor.voxelbridge$setUseAo(false);
                accessor.voxelbridge$setDefaultAo(false);

                model.emitBlockQuads(level, state, pos, blockInfo.randomSupplier, this);
            } finally {
                blockInfo.release();
                BlockRenderInfoAccessor accessor = (BlockRenderInfoAccessor) (Object) blockInfo;
                accessor.voxelbridge$setRandom(null);
            }
        }

        @Override
        protected AoCalculator createAoCalc(BlockRenderInfo blockInfo) {
            return new AoCalculator(blockInfo) {
                @Override
                public int light(BlockPos pos, BlockState state) {
                    return AoCalculator.getLightmapCoordinates(blockInfo.blockView, state, pos);
                }

                @Override
                public float ao(BlockPos pos, BlockState state) {
                    return AoLuminanceFix.INSTANCE.apply(blockInfo.blockView, pos, state);
                }
            };
        }

        @Override
        protected VertexConsumer getVertexConsumer(RenderType layer) {
            return null;
        }

        @Override
        public boolean isFaceCulled(Direction face) {
            return false;
        }

        @Override
        protected void bufferQuad(MutableQuadViewImpl quad, VertexConsumer vertexConsumer) {
            out.add(toBakedQuad(quad, spriteFinder));
        }
    }
}
