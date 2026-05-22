package com.voxelbridge.adapter;

import com.voxelbridge.export.quad.QuadDataUtil;
import com.voxelbridge.export.texture.SpriteKeyResolver;
import com.voxelbridge.platform.client.ClientAccessHolder;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;

import java.util.ArrayList;
import java.util.List;

public final class ForgeRenderAdapter implements RenderAdapter {
    private record QuadResult(List<BakedQuad> quads, QuadSource source) {
    }

    @Override
    public Object getBlockModel(BlockState state) {
        var modelManager = ClientAccessHolder.get().getModelManager();
        return modelManager != null ? modelManager.getBlockModelShaper().getBlockModel(state) : null;
    }

    @Override
    public List<BakedQuad> getQuads(Object model, BlockState state, BlockPos pos, BlockAndTintGetter level, long seed) {
        return buildQuadResult(model, state, pos, level, seed).quads();
    }

    @Override
    public QuadBatch getQuadBatch(Object model, BlockState state, BlockPos pos, BlockAndTintGetter level, long seed) {
        QuadResult result = buildQuadResult(model, state, pos, level, seed);
        return new QuadBatch(QuadDataUtil.wrapBakedQuads(result.quads()), result.source());
    }

    private QuadResult buildQuadResult(Object model, BlockState state, BlockPos pos, BlockAndTintGetter level, long seed) {
        List<BakedQuad> quads = new ArrayList<>();
        if (!(model instanceof BakedModel bakedModel)) {
            return new QuadResult(quads, QuadSource.PLATFORM_DEFAULT);
        }

        RandomSource random = RandomSource.create(seed);
        ModelData modelData = ModelData.EMPTY;
        try {
            for (Direction dir : Direction.values()) {
                List<BakedQuad> sideQuads = bakedModel.getQuads(state, dir, random, modelData, null);
                if (sideQuads != null) {
                    quads.addAll(sideQuads);
                }
            }
            List<BakedQuad> generalQuads = bakedModel.getQuads(state, null, random, modelData, null);
            if (generalQuads != null) {
                quads.addAll(generalQuads);
            }
        } catch (Throwable ignored) {
        }

        return new QuadResult(quads, QuadSource.PLATFORM_DEFAULT);
    }

    @Override
    public String getSpriteName(TextureAtlasSprite sprite) {
        return SpriteKeyResolver.resolve(sprite);
    }
}
