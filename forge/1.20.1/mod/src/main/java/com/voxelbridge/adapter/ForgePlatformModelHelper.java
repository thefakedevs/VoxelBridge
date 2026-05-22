package com.voxelbridge.adapter;

import com.voxelbridge.compat.ForgeQuadAccess;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;

public final class ForgePlatformModelHelper implements PlatformModelHelper {
    @Override
    public TextureAtlasSprite getQuadSprite(BakedQuad quad) {
        return ForgeQuadAccess.getSprite(quad);
    }

    @Override
    public Direction getQuadDirection(BakedQuad quad) {
        return ForgeQuadAccess.getDirection(quad);
    }

    @Override
    public int[] getQuadVertices(BakedQuad quad) {
        return ForgeQuadAccess.getVertices(quad);
    }

    @Override
    public int getQuadTintIndex(BakedQuad quad) {
        return ForgeQuadAccess.getTintIndex(quad);
    }
}
