package com.voxelbridge.adapter;

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
        RandomSource rand = RandomSource.create(seed);

        // Use vanilla API - no ModelData in Fabric
        try {
            for (Direction dir : Direction.values()) {
                List<BakedQuad> q = bakedModel.getQuads(state, dir, rand);
                if (q != null)
                    quads.addAll(q);
            }
            // Null direction (general quads)
            List<BakedQuad> q2 = bakedModel.getQuads(state, null, rand);
            if (q2 != null)
                quads.addAll(q2);
        } catch (Throwable ignored) {
        }

        return quads;
    }

    @Override
    public String getSpriteName(TextureAtlasSprite sprite) {
        return SpriteKeyResolver.resolve(sprite);
    }
}
