package com.voxelbridge.adapter;

import com.voxelbridge.export.quad.QuadDataUtil;
import com.voxelbridge.adapter.QuadBatch;
import com.voxelbridge.adapter.QuadSource;
import com.voxelbridge.export.texture.SpriteKeyResolver;
import com.voxelbridge.platform.client.ClientAccessHolder;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.extensions.IBakedModelExtension;
import net.neoforged.neoforge.client.model.data.ModelData;

import java.util.ArrayList;
import java.util.List;

public class NeoForgeRenderAdapter implements RenderAdapter {
    
    public NeoForgeRenderAdapter() {
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
        RandomSource rand = RandomSource.create(seed);

        ModelData modelData = ModelData.EMPTY;
        if (level instanceof Level l) {
            try {
                modelData = l.getModelData(pos);
            } catch (Throwable ignored) {
            }
        }

        try {
            if (bakedModel instanceof IBakedModelExtension extension) {
                if (level instanceof Level l) {
                    modelData = extension.getModelData(l, pos, state, modelData);
                }
            }
        } catch (Throwable ignored) {}

        try {
            for (Direction dir : Direction.values()) {
                List<BakedQuad> q = bakedModel.getQuads(state, dir, rand, modelData, null);
                if (q != null) quads.addAll(q);
            }
            List<BakedQuad> q2 = bakedModel.getQuads(state, null, rand, modelData, null);
            if (q2 != null) quads.addAll(q2);
        } catch (Throwable ignored) {}

        return quads;
    }

    @Override
    public QuadBatch getQuadBatch(Object model, BlockState state, BlockPos pos, BlockAndTintGetter level, long seed) {
        return new QuadBatch(QuadDataUtil.wrapBakedQuads(getQuads(model, state, pos, level, seed)), QuadSource.PLATFORM_DEFAULT);
    }

    @Override
    public String getSpriteName(TextureAtlasSprite sprite) {
        return SpriteKeyResolver.resolve(sprite);
    }

    private TextureAtlas getBlockAtlas() {
        var modelManager = ClientAccessHolder.get().getModelManager();
        return modelManager != null ? modelManager.getAtlas(TextureAtlas.LOCATION_BLOCKS) : null;
    }
}
