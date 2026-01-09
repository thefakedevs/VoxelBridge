package com.voxelbridge.export.texture;

import com.voxelbridge.core.texture.AnimationMetadata;
import com.voxelbridge.core.texture.TextureAccess;
import com.voxelbridge.platform.client.ClientAccessHolder;
import com.voxelbridge.platform.texture.TextureLoader;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.Set;
import java.util.stream.Collectors;

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

    @Override
    public AnimationMetadata readAnimationMetadata(String resourceKey) {
        if (resourceKey == null) {
            return null;
        }
        try {
            var rm = ClientAccessHolder.get().getResourceManager();
            ResourceLocation loc = ResourceLocation.parse(resourceKey);
            var resOpt = rm.getResource(loc);
            if (resOpt.isEmpty()) {
                return null;
            }
            var res = resOpt.get();
            var meta = AnimationMetadataUtil.readSection(res.metadata());
            return meta == null ? null : AnimationMetadataUtil.toCoreMetadata(meta);
        } catch (Exception ignored) {
            return null;
        }
    }

    @Override
    public boolean hasResource(String resourceKey) {
        if (resourceKey == null) {
            return false;
        }
        try {
            var rm = ClientAccessHolder.get().getResourceManager();
            return rm.getResource(ResourceLocation.parse(resourceKey)).isPresent();
        } catch (Exception ignored) {
            return false;
        }
    }

    @Override
    public Set<String> listPngResources(String pathPrefix) {
        if (pathPrefix == null) {
            return Set.of();
        }
        String cleanPath = pathPrefix.endsWith("/") ? pathPrefix.substring(0, pathPrefix.length() - 1) : pathPrefix;
        try {
            var rm = ClientAccessHolder.get().getResourceManager();
            return rm.listResources(cleanPath, loc -> loc.getPath().endsWith(".png"))
                .keySet()
                .stream()
                .map(ResourceLocation::toString)
                .collect(Collectors.toSet());
        } catch (Exception ignored) {
            return Set.of();
        }
    }

    @Override
    public InputStream openResource(String resourceKey) {
        if (resourceKey == null) {
            return null;
        }
        try {
            var rm = ClientAccessHolder.get().getResourceManager();
            var resOpt = rm.getResource(ResourceLocation.parse(resourceKey));
            if (resOpt.isEmpty()) {
                return null;
            }
            return resOpt.get().open();
        } catch (Exception ignored) {
            return null;
        }
    }
}
