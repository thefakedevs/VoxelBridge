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
}
