package com.voxelbridge.adapter;

import com.voxelbridge.adapter.QuadBatch;
import com.voxelbridge.adapter.QuadSource;
import com.voxelbridge.export.texture.SpriteKeyResolver;
import com.voxelbridge.export.quad.QuadDataUtil;
import com.voxelbridge.platform.render.frapi.FabricRenderApiHelper;
import com.voxelbridge.platform.client.ClientAccessHolder;
import com.voxelbridge.util.debug.LogModule;
import com.voxelbridge.util.debug.VoxelBridgeLogger;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel;
import net.fabricmc.fabric.api.renderer.v1.model.SpriteFinder;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

public class FabricRenderAdapter implements RenderAdapter {

    public FabricRenderAdapter() {
    }

    @Override
    public Object getBlockModel(BlockState state) {
        var modelManager = ClientAccessHolder.get().getModelManager();
        if (modelManager == null) {
            return null;
        }
        return modelManager.getBlockModelShaper().getBlockModel(state);
    }

    @Override
    public List<BakedQuad> getQuads(Object model, BlockState state, BlockPos pos, BlockAndTintGetter level, long seed) {
        List<BakedQuad> quads = new ArrayList<>();
        if (!(model instanceof BakedModel bakedModel)) {
            return quads;
        }
        SpriteFinder spriteFinder = getSpriteFinder();
        List<BakedQuad> frapiQuads = new ArrayList<>();
        if (spriteFinder != null && bakedModel instanceof FabricBakedModel) {
            FabricBakedModel fabricModel = (FabricBakedModel) bakedModel;
            RandomSource frapiRand = RandomSource.create(seed);
            frapiQuads = FabricRenderApiHelper.extractQuads(fabricModel, level, state, pos, frapiRand, seed, spriteFinder);
        } else if (VoxelBridgeLogger.isDebugEnabled(LogModule.EXPORT)) {
            VoxelBridgeLogger.debug(LogModule.EXPORT, String.format(
                "[FRAPI] Skipped: spriteFinder=%s fabricModel=%s",
                spriteFinder != null,
                bakedModel instanceof FabricBakedModel
            ));
        }

        boolean logStats = VoxelBridgeLogger.isDebugEnabled(LogModule.EXPORT);
        List<BakedQuad> vanillaQuads = new ArrayList<>();
        if (frapiQuads.isEmpty() || logStats) {
            RandomSource vanillaRand = RandomSource.create(seed);
            for (Direction dir : Direction.values()) {
                vanillaQuads.addAll(bakedModel.getQuads(state, dir, vanillaRand));
            }
            vanillaQuads.addAll(bakedModel.getQuads(state, null, vanillaRand));
        }

        if (logStats) {
            boolean rendererPresent = RendererAccess.INSTANCE.getRenderer() != null;
            String used = !frapiQuads.isEmpty() ? "frapi" : (!vanillaQuads.isEmpty() ? "vanilla" : "none");
            VoxelBridgeLogger.debug(LogModule.EXPORT, String.format(
                "[QuadStats] model=%s state=%s pos=%s vanilla=%d frapi=%d renderer=%s used=%s",
                bakedModel.getClass().getSimpleName(),
                state != null ? state.toString() : "null",
                pos != null ? pos.toShortString() : "null",
                vanillaQuads.size(),
                frapiQuads.size(),
                rendererPresent,
                used
            ));
        }

        if (!frapiQuads.isEmpty()) {
            return frapiQuads;
        }
        if (!vanillaQuads.isEmpty()) {
            return vanillaQuads;
        }
        return vanillaQuads;
    }

    @Override
    public QuadBatch getQuadBatch(Object model, BlockState state, BlockPos pos, BlockAndTintGetter level, long seed) {
        return new QuadBatch(QuadDataUtil.wrapBakedQuads(getQuads(model, state, pos, level, seed)), QuadSource.FRAPI);
    }

    @Override
    public String getSpriteName(TextureAtlasSprite sprite) {
        return SpriteKeyResolver.resolve(sprite);
    }

    private SpriteFinder getSpriteFinder() {
        var modelManager = ClientAccessHolder.get().getModelManager();
        if (modelManager == null) {
            return null;
        }
        TextureAtlas atlas = modelManager.getAtlas(TextureAtlas.LOCATION_BLOCKS);
        return SpriteFinder.get(atlas);
    }
}
