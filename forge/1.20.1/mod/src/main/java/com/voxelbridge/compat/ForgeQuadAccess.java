package com.voxelbridge.compat;

import com.voxelbridge.mixin.BakedQuadAccessor;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;

public final class ForgeQuadAccess {
    private ForgeQuadAccess() {
    }

    public static TextureAtlasSprite getSprite(BakedQuad quad) {
        return quad != null ? ((BakedQuadAccessor) quad).voxelbridge$getSprite() : null;
    }

    public static Direction getDirection(BakedQuad quad) {
        return quad != null ? ((BakedQuadAccessor) quad).voxelbridge$getDirection() : null;
    }

    public static int[] getVertices(BakedQuad quad) {
        if (quad == null) {
            return new int[0];
        }
        int[] vertices = ((BakedQuadAccessor) quad).voxelbridge$getVertices();
        return vertices != null ? vertices : new int[0];
    }

    public static int getTintIndex(BakedQuad quad) {
        return quad != null ? ((BakedQuadAccessor) quad).voxelbridge$getTintIndex() : -1;
    }
}
