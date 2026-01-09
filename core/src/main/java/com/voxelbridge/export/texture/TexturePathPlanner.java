package com.voxelbridge.export.texture;

import java.util.Map;

/**
 * Core path planning utilities for texture exports.
 */
public final class TexturePathPlanner {

    private TexturePathPlanner() {}

    public static String ensureSpritePath(Map<String, String> materialPaths, String spriteKey) {
        String existing = materialPaths.get(spriteKey);
        if (existing != null) {
            return existing;
        }
        String rel = spritePath(spriteKey);
        materialPaths.put(spriteKey, rel);
        return rel;
    }

    public static String spritePath(String spriteKey) {
        return "textures/" + TexturePathResolver.safe(spriteKey) + ".png";
    }
}
