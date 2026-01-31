package com.voxelbridge.compat;

import com.voxelbridge.mixin.BakedQuadAccessor;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
/**
 * Fabric-specific BakedQuad access using Mixin accessor.
 */
public final class FabricQuadAccess {

    private FabricQuadAccess() {
    }

    public static TextureAtlasSprite getSprite(BakedQuad quad) {
        if (quad == null)
            return null;
        try {
            return ((BakedQuadAccessor) quad).voxelbridge$getSprite();
        } catch (Throwable t) {
            TextureAtlasSprite sprite = (TextureAtlasSprite) tryInvoke(quad, "getSprite");
            return sprite;
        }
    }

    public static Direction getDirection(BakedQuad quad) {
        if (quad == null)
            return null;
        try {
            return ((BakedQuadAccessor) quad).voxelbridge$getDirection();
        } catch (Throwable t) {
            return (Direction) tryInvoke(quad, "getDirection");
        }
    }

    public static int[] getVertices(BakedQuad quad) {
        if (quad == null)
            return new int[0];
        try {
            int[] v = ((BakedQuadAccessor) quad).voxelbridge$getVertices();
            return v != null ? v : new int[0];
        } catch (Throwable t) {
            Object v = tryInvoke(quad, "getVertices");
            if (v instanceof int[] arr) {
                return arr;
            }
            return new int[0];
        }
    }

    public static int getTintIndex(BakedQuad quad) {
        if (quad == null)
            return -1;
        try {
            return ((BakedQuadAccessor) quad).voxelbridge$getTintIndex();
        } catch (Throwable t) {
            Object v = tryInvoke(quad, "getTintIndex");
            return v instanceof Integer ? (Integer) v : -1;
        }
    }

    private static Object tryInvoke(Object target, String methodName) {
        if (target == null) {
            return null;
        }
        try {
            var method = target.getClass().getMethod(methodName);
            method.setAccessible(true);
            return method.invoke(target);
        } catch (Exception ignored) {
            return null;
        }
    }
}
