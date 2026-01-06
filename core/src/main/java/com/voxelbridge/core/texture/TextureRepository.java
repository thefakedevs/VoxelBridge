package com.voxelbridge.core.texture;

import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Session-scoped texture repository used to avoid static cache leakage.
 * Supports both resource-key strings and spriteKey-based keys for generated or virtual textures.
 */
public final class TextureRepository {
    private final Map<String, BufferedImage> locationCache = new ConcurrentHashMap<>();
    private final Map<String, String> keyToLocation = new ConcurrentHashMap<>();
    private final Map<String, BufferedImage> spriteCache = new ConcurrentHashMap<>();
    private final Map<String, AnimatedFrameSet> animatedCache = new ConcurrentHashMap<>();

    public BufferedImage get(String resourceKey) {
        return resourceKey == null ? null : locationCache.get(resourceKey);
    }

    public BufferedImage getBySpriteKey(String spriteKey) {
        if (spriteKey == null) return null;
        String resourceKey = keyToLocation.get(spriteKey);
        if (resourceKey != null) {
            BufferedImage img = locationCache.get(resourceKey);
            if (img != null) return img;
        }
        return spriteCache.get(spriteKey);
    }

    public boolean contains(String resourceKey) {
        return resourceKey != null && locationCache.containsKey(resourceKey);
    }

    /**
     * Put an image with both resourceKey and optional spriteKey mapping.
     * If resourceKey is provided, it is stored in locationCache; spriteKey will map to resourceKey.
     * If spriteKey is provided without loc, it will be stored in spriteCache (generated/virtual).
     */
    public BufferedImage put(String resourceKey, String spriteKey, BufferedImage img) {
        if (img == null) return null;
        if (resourceKey != null) {
            locationCache.put(resourceKey, img);
            if (spriteKey != null) {
                keyToLocation.put(spriteKey, resourceKey);
            }
        } else if (spriteKey != null) {
            spriteCache.put(spriteKey, img);
        }
        return img;
    }

    public BufferedImage computeIfAbsent(String resourceKey, Function<String, BufferedImage> loader) {
        if (resourceKey == null) return null;
        return locationCache.computeIfAbsent(resourceKey, loader);
    }

    public void register(String spriteKey, String resourceKey) {
        if (spriteKey != null && resourceKey != null) {
            keyToLocation.put(spriteKey, resourceKey);
        }
    }

    public String getRegisteredLocation(String spriteKey) {
        return keyToLocation.get(spriteKey);
    }

    public Map<String, String> getRegisteredTextures() {
        return keyToLocation;
    }

    /** Store a generated texture keyed only by spriteKey. */
    public void putGenerated(String spriteKey, BufferedImage image) {
        if (spriteKey != null && image != null) {
            spriteCache.put(spriteKey, image);
        }
    }

    public void putAnimation(String spriteKey, AnimatedFrameSet frames) {
        // Accept empty frames as markers (indicates animation exists but frames couldn't be extracted)
        if (spriteKey != null && frames != null) {
            animatedCache.put(spriteKey, frames);
        }
    }

    public AnimatedFrameSet getAnimation(String spriteKey) {
        return spriteKey == null ? null : animatedCache.get(spriteKey);
    }

    public boolean hasAnimation(String spriteKey) {
        return spriteKey != null && animatedCache.containsKey(spriteKey);
    }

    public Map<String, AnimatedFrameSet> getAnimatedCache() {
        return animatedCache;
    }

    public Set<String> getSpriteKeys() {
        return spriteCache.keySet();
    }

    /**
     * Exposes the sprite-keyed cache (generated/virtual textures).
     */
    public Map<String, BufferedImage> getSpriteCache() {
        return spriteCache;
    }

    public void clear() {
        locationCache.clear();
        keyToLocation.clear();
        spriteCache.clear();
        animatedCache.clear();
    }
}
