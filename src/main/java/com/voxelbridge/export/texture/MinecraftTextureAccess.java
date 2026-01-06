package com.voxelbridge.export.texture;

import com.voxelbridge.core.texture.TextureAccess;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.awt.image.BufferedImage;

/**
 * Minecraft-backed texture access adapter.
 */
@OnlyIn(Dist.CLIENT)
public final class MinecraftTextureAccess implements TextureAccess<TextureAtlasSprite> {

    public static final MinecraftTextureAccess INSTANCE = new MinecraftTextureAccess();

    private MinecraftTextureAccess() {}

    @Override
    public String spriteKeyToResourceKey(String spriteKey) {
        if (spriteKey == null) {
            return null;
        }
        return TextureLoader.spriteKeyToTexturePNG(spriteKey).toString();
    }

    @Override
    public BufferedImage readTexture(String resourceKey, boolean preserveAnimationStrip) {
        if (resourceKey == null) {
            return null;
        }
        ResourceLocation loc = ResourceLocation.parse(resourceKey);
        return TextureLoader.readTexture(loc, preserveAnimationStrip);
    }

    @Override
    public BufferedImage readSprite(TextureAtlasSprite sprite) {
        return sprite == null ? null : TextureLoader.fromSprite(sprite);
    }

    @Override
    public String resolveSpriteKey(TextureAtlasSprite sprite) {
        return sprite == null ? null : SpriteKeyResolver.resolve(sprite);
    }
}
