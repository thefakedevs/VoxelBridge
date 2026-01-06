package com.voxelbridge.export.texture;

import com.voxelbridge.core.texture.AnimationMetadata;
import com.voxelbridge.core.texture.TextureAccess;
import com.voxelbridge.platform.texture.TextureLoader;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
        return sprite == null ? null : com.voxelbridge.adapter.Adapters.getRender().getSpriteName(sprite);
    }

    @Override
    public AnimationMetadata readAnimationMetadata(String resourceKey) {
        if (resourceKey == null) {
            return null;
        }
        try {
            var rm = net.minecraft.client.Minecraft.getInstance().getResourceManager();
            ResourceLocation loc = ResourceLocation.parse(resourceKey);
            var resOpt = rm.getResource(loc);
            if (resOpt.isEmpty()) {
                return null;
            }
            var res = resOpt.get();
            Optional<AnimationMetadataSection> metaOpt =
                res.metadata().getSection(AnimationMetadataSection.SERIALIZER);
            AnimationMetadataSection meta = metaOpt.orElse(null);
            if (meta == null) {
                return null;
            }
            List<AnimationMetadata.FrameTiming> timings = new ArrayList<>();
            meta.forEachFrame((idx, time) -> timings.add(new AnimationMetadata.FrameTiming(idx, time)));
            boolean interpolate = false;
            try {
                interpolate = meta.isInterpolatedFrames();
            } catch (NoSuchMethodError ignored) {
            }
            return new AnimationMetadata(meta.getDefaultFrameTime(), timings, interpolate, 0, 0);
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
            var rm = net.minecraft.client.Minecraft.getInstance().getResourceManager();
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
            var rm = net.minecraft.client.Minecraft.getInstance().getResourceManager();
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
            var rm = net.minecraft.client.Minecraft.getInstance().getResourceManager();
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
