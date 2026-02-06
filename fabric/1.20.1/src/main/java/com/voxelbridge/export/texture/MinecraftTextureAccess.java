package com.voxelbridge.export.texture;

import com.voxelbridge.core.texture.AnimationMetadata;
import com.voxelbridge.core.texture.TextureAccess;
import com.voxelbridge.platform.client.ClientAccessHolder;
import com.voxelbridge.platform.texture.TextureLoader;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Minecraft-backed texture access adapter.
 */
public final class MinecraftTextureAccess implements TextureAccess<TextureAtlasSprite> {

    public static final MinecraftTextureAccess INSTANCE = new MinecraftTextureAccess();

    private MinecraftTextureAccess() {}

    @Override
    public String spriteKeyToResourceKey(String spriteKey) {
        if (spriteKey == null) {
            return null;
        }
        try {
            return TextureLoader.spriteKeyToTexturePNG(spriteKey).toString();
        } catch (Exception ignored) {
            return null;
        }
    }

    @Override
    public BufferedImage readTexture(String resourceKey, boolean preserveAnimationStrip) {
        if (resourceKey == null) {
            return null;
        }
        try {
            ResourceLocation loc = new ResourceLocation(resourceKey);
            ResourceLocation normalized = normalizeDynamicMapLocation(loc);
            if (normalized != null) {
                loc = normalized;
            }
            return TextureLoader.readTexture(loc, preserveAnimationStrip);
        } catch (Exception ignored) {
            return null;
        }
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
            ResourceLocation loc = new ResourceLocation(resourceKey);
            var resOpt = rm.getResource(loc);
            if (resOpt.isEmpty()) {
                return null;
            }
            var res = resOpt.get();
            var meta = AnimationMetadataUtil.readSection(res.metadata());
            if (meta != null) {
                return AnimationMetadataUtil.toCoreMetadata(meta);
            }

            ResourceLocation metaLoc = new ResourceLocation(resourceKey + ".mcmeta");
            var metaResOpt = rm.getResource(metaLoc);
            if (metaResOpt.isEmpty()) {
                return null;
            }
            try (InputStream in = metaResOpt.get().open()) {
                if (in == null) {
                    return null;
                }
                String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                return AnimationMetadataUtil.parseMcmetaJson(json);
            }
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
            return rm.getResource(new ResourceLocation(resourceKey)).isPresent();
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
            var resOpt = rm.getResource(new ResourceLocation(resourceKey));
            if (resOpt.isEmpty()) {
                return null;
            }
            return resOpt.get().open();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static ResourceLocation normalizeDynamicMapLocation(ResourceLocation loc) {
        if (loc == null) {
            return null;
        }
        String path = loc.getPath();
        if (path == null) {
            return null;
        }
        // normalize textures/maps/<id>.png -> map/<id>
        if (path.startsWith("textures/maps/")) {
            String file = path.substring("textures/maps/".length());
            int dot = file.indexOf('.');
            if (dot > 0) {
                file = file.substring(0, dot);
            }
            try {
                int id = Integer.parseInt(file);
                return new ResourceLocation(loc.getNamespace(), "map/" + id);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        // normalize textures/map/<id>.png -> map/<id>
        if (path.startsWith("textures/map/")) {
            String file = path.substring("textures/map/".length());
            int dot = file.indexOf('.');
            if (dot > 0) {
                file = file.substring(0, dot);
            }
            try {
                int id = Integer.parseInt(file);
                return new ResourceLocation(loc.getNamespace(), "map/" + id);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        // normalize textures/dynamic/map/<id>_<frame>.png -> maps/<id>
        if (path.startsWith("textures/dynamic/map/")) {
            String file = path.substring("textures/dynamic/map/".length());
            int dot = file.indexOf('.');
            if (dot > 0) {
                file = file.substring(0, dot);
            }
            int underscore = file.indexOf('_');
            String idStr = underscore > 0 ? file.substring(0, underscore) : file;
            try {
                int id = Integer.parseInt(idStr);
                return new ResourceLocation(loc.getNamespace(), "map/" + id);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        // normalize maps/<id> -> map/<id>
        if (path.startsWith("maps/")) {
            String idStr = path.substring("maps/".length());
            try {
                int id = Integer.parseInt(idStr);
                return new ResourceLocation(loc.getNamespace(), "map/" + id);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
