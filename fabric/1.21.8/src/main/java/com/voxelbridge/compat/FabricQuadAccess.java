package com.voxelbridge.compat;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;

/**
 * Fabric-specific BakedQuad access using public API.
 */
public final class FabricQuadAccess {

    private FabricQuadAccess() {
    }

    public static TextureAtlasSprite getSprite(BakedQuad quad) {
        if (quad == null)
            return null;
        try {
            return quad.sprite();
        } catch (Throwable t) {
            return null;
        }
    }

    public static Direction getDirection(BakedQuad quad) {
        if (quad == null)
            return null;
        try {
            return quad.direction();
        } catch (Throwable t) {
            return null;
        }
    }

    public static int[] getVertices(BakedQuad quad) {
        if (quad == null)
            return new int[0];
        try {
            int[] v = quad.vertices();
            return v != null ? v : new int[0];
        } catch (Throwable t) {
            return new int[0];
        }
    }

    public static int getTintIndex(BakedQuad quad) {
        if (quad == null)
            return -1;
        try {
            return quad.tintIndex();
        } catch (Throwable t) {
            return -1;
        }
    }
}
