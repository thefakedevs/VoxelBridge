package com.voxelbridge.adapter;

import com.voxelbridge.export.texture.SpriteKeyResolver;
import com.voxelbridge.modhandler.frapi.FrapiCompat;
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
        return getQuadBatch(model, state, pos, level, seed).quads();
    }

    @Override
    public QuadBatch getQuadBatch(Object model, BlockState state, BlockPos pos, BlockAndTintGetter level, long seed) {
        List<BakedQuad> quads = new ArrayList<>();
        if (!(model instanceof BakedModel bakedModel)) {
            return new QuadBatch(quads, QuadSource.PLATFORM_DEFAULT);
        }
        RandomSource rand = RandomSource.create(seed);
        Object spriteFinder = getSpriteFinder();
        
        // 1. Get ModelData (NeoForge specific)
        ModelData modelData = ModelData.EMPTY;
        if (level instanceof Level l) {
             try {
                modelData = l.getModelData(pos);
            } catch (Throwable ignored) {}
        }
        
        try {
            if (bakedModel instanceof IBakedModelExtension extension) {
                if (level instanceof Level l) {
                    modelData = extension.getModelData(l, pos, state, modelData);
                }
            }
        } catch (Throwable ignored) {}

        // 2. Try Fabric API (for CTM/Indium support on NeoForge)
        if (spriteFinder != null) {
            List<BakedQuad> fabricQuads = FrapiCompat.extractQuads(bakedModel, level, state, pos, rand, spriteFinder);
            if (!fabricQuads.isEmpty()) {
                return new QuadBatch(fabricQuads, QuadSource.FRAPI);
            }
        }

        // 3. Fallback to Vanilla/NeoForge Standard API
        try {
            for (Direction dir : Direction.values()) {
                List<BakedQuad> q = bakedModel.getQuads(state, dir, rand, modelData, null);
                if (q != null) quads.addAll(q);
            }
            // Null direction (general quads)
            List<BakedQuad> q2 = bakedModel.getQuads(state, null, rand, modelData, null);
            if (q2 != null) quads.addAll(q2);
        } catch (Throwable ignored) {}

        return new QuadBatch(quads, QuadSource.PLATFORM_DEFAULT);
    }

    @Override
    public String getSpriteName(TextureAtlasSprite sprite) {
        return SpriteKeyResolver.resolve(sprite);
    }

    private Object getSpriteFinder() {
        var modelManager = ClientAccessHolder.get().getModelManager();
        if (modelManager == null) {
            return null;
        }
        var atlas = modelManager.getAtlas(TextureAtlas.LOCATION_BLOCKS);
        return FrapiCompat.getSpriteFinder(atlas);
    }
}
