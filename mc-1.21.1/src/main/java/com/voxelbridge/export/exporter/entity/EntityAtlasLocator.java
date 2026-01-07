package com.voxelbridge.export.exporter.entity;

import com.voxelbridge.export.exporter.resolve.AtlasLocator;
import com.voxelbridge.platform.client.ClientAccess;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Finds the sprite inside an atlas that contains a given UV coordinate.
 */
@OnlyIn(Dist.CLIENT)
final class EntityAtlasLocator implements AtlasLocator {
    private final ClientAccess clientAccess;

    EntityAtlasLocator(ClientAccess clientAccess) {
        this.clientAccess = clientAccess;
    }

    @Override
    public TextureAtlasSprite find(ResourceLocation atlasLocation, float u, float v) {
        if (atlasLocation == null) {
            return null;
        }
        var tex = clientAccess.getTextureManager().getTexture(atlasLocation);
        if (!(tex instanceof TextureAtlas atlas)) {
            return null;
        }
        for (TextureAtlasSprite sprite : atlas.getTextures().values()) {
            if (contains(sprite, u, v)) {
                return sprite;
            }
        }
        return null;
    }

    private boolean contains(TextureAtlasSprite sprite, float u, float v) {
        return u >= sprite.getU0() && u <= sprite.getU1() && v >= sprite.getV0() && v <= sprite.getV1();
    }
}
