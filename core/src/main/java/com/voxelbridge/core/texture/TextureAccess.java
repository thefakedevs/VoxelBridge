package com.voxelbridge.core.texture;

import java.awt.image.BufferedImage;

/**
 * Platform-provided texture access for sprite and resource resolution.
 * Implementations may wrap Minecraft or other runtimes.
 */
public interface TextureAccess<S> {
    /**
     * Convert a sprite key (e.g. "minecraft:block/stone") to a resource key string.
     */
    String spriteKeyToResourceKey(String spriteKey);

    /**
     * Read a texture by resource key (namespace:path) with optional animation strip preservation.
     */
    BufferedImage readTexture(String resourceKey, boolean preserveAnimationStrip);

    /**
     * Read a texture by resource key using the platform default behavior.
     */
    default BufferedImage readTexture(String resourceKey) {
        return readTexture(resourceKey, false);
    }

    /**
     * Read a texture from a runtime sprite handle.
     */
    BufferedImage readSprite(S sprite);

    /**
     * Resolve a deterministic sprite key from a runtime sprite handle.
     */
    String resolveSpriteKey(S sprite);

    /**
     * Read animation metadata for a texture resource key (namespace:path).
     */
    default AnimationMetadata readAnimationMetadata(String resourceKey) {
        return null;
    }

    /**
     * Check if a resource exists for the given key.
     */
    default boolean hasResource(String resourceKey) {
        return false;
    }

    /**
     * List PNG resource keys under a path prefix (e.g., "textures/block").
     */
    default java.util.Set<String> listPngResources(String pathPrefix) {
        return java.util.Set.of();
    }

    /**
     * Open a resource stream for the given key (caller closes).
     */
    default java.io.InputStream openResource(String resourceKey) throws java.io.IOException {
        return null;
    }

    /**
     * Ensure the resource key points to a PNG under textures/.
     */
    default String ensurePngKey(String resourceKey) {
        if (resourceKey == null) {
            return null;
        }
        int split = resourceKey.indexOf(':');
        if (split <= 0 || split == resourceKey.length() - 1) {
            return resourceKey;
        }
        String namespace = resourceKey.substring(0, split);
        String path = resourceKey.substring(split + 1);
        if (!path.startsWith("textures/")) {
            path = "textures/" + path;
        }
        if (!path.endsWith(".png")) {
            path = path + ".png";
        }
        return namespace + ":" + path;
    }

    /**
     * Append a suffix before the .png extension.
     */
    default String appendSuffixKey(String resourceKey, String suffix) {
        if (resourceKey == null || suffix == null) {
            return resourceKey;
        }
        String key = ensurePngKey(resourceKey);
        int split = key.indexOf(':');
        if (split <= 0 || split == key.length() - 1) {
            return key;
        }
        String namespace = key.substring(0, split);
        String path = key.substring(split + 1);
        String withoutPng = path.endsWith(".png") ? path.substring(0, path.length() - 4) : path;
        return namespace + ":" + withoutPng + suffix + ".png";
    }

    /**
     * Build a generated resource key from namespace and path.
     */
    default String generatedKey(String namespace, String path) {
        if (namespace == null || namespace.isEmpty()) {
            namespace = "minecraft";
        }
        if (path == null) {
            path = "generated/missingno.png";
        }
        return namespace + ":" + path;
    }
}
