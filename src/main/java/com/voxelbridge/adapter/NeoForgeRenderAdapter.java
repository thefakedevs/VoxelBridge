package com.voxelbridge.adapter;

import com.voxelbridge.export.texture.SpriteKeyResolver;
import com.voxelbridge.modhandler.frapi.FabricApiHelper;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel;
import net.fabricmc.fabric.api.renderer.v1.model.SpriteFinder;
import net.minecraft.client.Minecraft;
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
    public BakedModel getBlockModel(BlockState state) {
        var modelManager = Minecraft.getInstance().getModelManager();
        if (modelManager == null) {
            return null;
        }
        return modelManager.getBlockModelShaper().getBlockModel(state);
    }

    @Override
    public List<BakedQuad> getQuads(BakedModel model, BlockState state, BlockPos pos, BlockAndTintGetter level, long seed) {
        List<BakedQuad> quads = new ArrayList<>();
        RandomSource rand = RandomSource.create(seed);
        SpriteFinder spriteFinder = getSpriteFinder();
        
        // 1. Get ModelData (NeoForge specific)
        ModelData modelData = ModelData.EMPTY;
        if (level instanceof Level l) {
             try {
                modelData = l.getModelData(pos);
            } catch (Throwable ignored) {}
        }
        
        try {
            if (model instanceof IBakedModelExtension extension) {
                if (level instanceof Level l) {
                    modelData = extension.getModelData(l, pos, state, modelData);
                }
            }
        } catch (Throwable ignored) {}

        // 2. Try Fabric API (for CTM/Indium support on NeoForge)
        if (spriteFinder != null && model instanceof FabricBakedModel fabricModel && !fabricModel.isVanillaAdapter()) {
            List<BakedQuad> fabricQuads = FabricApiHelper.extractQuads(fabricModel, level, state, pos, rand, spriteFinder);
            if (!fabricQuads.isEmpty()) {
                return fabricQuads;
            }
        }

        // 3. Fallback to Vanilla/NeoForge Standard API
        try {
            for (Direction dir : Direction.values()) {
                List<BakedQuad> q = model.getQuads(state, dir, rand, modelData, null);
                if (q != null) quads.addAll(q);
            }
            // Null direction (general quads)
            List<BakedQuad> q2 = model.getQuads(state, null, rand, modelData, null);
            if (q2 != null) quads.addAll(q2);
        } catch (Throwable ignored) {}

        return quads;
    }

    @Override
    public String getSpriteName(TextureAtlasSprite sprite) {
        return SpriteKeyResolver.resolve(sprite);
    }

    private SpriteFinder getSpriteFinder() {
        var modelManager = Minecraft.getInstance().getModelManager();
        if (modelManager == null) {
            return null;
        }
        var atlas = modelManager.getAtlas(TextureAtlas.LOCATION_BLOCKS);
        return SpriteFinder.get(atlas);
    }
}
