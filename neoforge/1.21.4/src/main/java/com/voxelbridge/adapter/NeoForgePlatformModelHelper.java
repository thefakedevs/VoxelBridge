package com.voxelbridge.adapter;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;

/**
 * NeoForge 1.21.4 implementation of PlatformModelHelper.
 * In 1.21.4, BakedQuad exposes the needed accessors.
 */
public class NeoForgePlatformModelHelper implements PlatformModelHelper {

    @Override
    public TextureAtlasSprite getQuadSprite(BakedQuad quad) {
        if (quad == null)
            return null;
        try {
            return quad.getSprite();
        } catch (Throwable t) {
            return null;
        }
    }

    @Override
    public Direction getQuadDirection(BakedQuad quad) {
        if (quad == null)
            return null;
        try {
            return quad.getDirection();
        } catch (Throwable t) {
            return null;
        }
    }

    @Override
    public int[] getQuadVertices(BakedQuad quad) {
        if (quad == null)
            return new int[0];
        try {
            int[] vertices = quad.getVertices();
            return vertices != null ? vertices : new int[0];
        } catch (Throwable t) {
            return new int[0];
        }
    }

    @Override
    public int getQuadTintIndex(BakedQuad quad) {
        if (quad == null)
            return -1;
        try {
            return quad.getTintIndex();
        } catch (Throwable t) {
            return -1;
        }
    }
}
