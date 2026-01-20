package com.voxelbridge.adapter;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;

/**
 * NeoForge implementation of PlatformModelHelper.
 * Directly accesses BakedQuad methods as they are public in this environment.
 */
public class NeoForgePlatformModelHelper implements PlatformModelHelper {

    @Override
    public TextureAtlasSprite getQuadSprite(BakedQuad quad) {
        if (quad == null)
            return null;
        return quad.getSprite();
    }

    @Override
    public Direction getQuadDirection(BakedQuad quad) {
        if (quad == null)
            return null;
        return quad.getDirection();
    }

    @Override
    public int[] getQuadVertices(BakedQuad quad) {
        if (quad == null)
            return new int[0];
        return quad.getVertices();
    }

    @Override
    public int getQuadTintIndex(BakedQuad quad) {
        if (quad == null)
            return -1;
        return quad.getTintIndex();
    }
}
