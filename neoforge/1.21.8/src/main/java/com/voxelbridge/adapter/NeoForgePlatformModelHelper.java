package com.voxelbridge.adapter;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;

/**
 * NeoForge 1.21.8 implementation of PlatformModelHelper.
 * In 1.21.8, BakedQuad methods are not directly accessible, so we need Mixin accessors.
 */
public class NeoForgePlatformModelHelper implements PlatformModelHelper {

    @Override
    public TextureAtlasSprite getQuadSprite(BakedQuad quad) {
        if (quad == null)
            return null;
        // 1.21.8: Use Mixin accessor
        return ((com.voxelbridge.mixin.BakedQuadAccessor) (Object) quad).voxelbridge$getSprite();
    }

    @Override
    public Direction getQuadDirection(BakedQuad quad) {
        if (quad == null)
            return null;
        // 1.21.8: Use Mixin accessor
        return ((com.voxelbridge.mixin.BakedQuadAccessor) (Object) quad).voxelbridge$getDirection();
    }

    @Override
    public int[] getQuadVertices(BakedQuad quad) {
        if (quad == null)
            return new int[0];
        // 1.21.8: Use Mixin accessor
        return ((com.voxelbridge.mixin.BakedQuadAccessor) (Object) quad).voxelbridge$getVertices();
    }

    @Override
    public int getQuadTintIndex(BakedQuad quad) {
        if (quad == null)
            return -1;
        // 1.21.8: Use Mixin accessor
        return ((com.voxelbridge.mixin.BakedQuadAccessor) (Object) quad).voxelbridge$getTintIndex();
    }
}
