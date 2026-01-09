package com.voxelbridge.export.texture;

import java.util.Map;

/**
 * Core rules for sprite export classification and path resolution.
 */
public final class TextureExportPlanner {

    private TextureExportPlanner() {}

    public static boolean isEntityLike(String spriteKey) {
        return spriteKey != null && (spriteKey.startsWith("blockentity:")
            || spriteKey.startsWith("entity:")
            || spriteKey.startsWith("base:"));
    }

    public static boolean isPbrSprite(String spriteKey) {
        return spriteKey != null && (spriteKey.endsWith("_n") || spriteKey.endsWith("_s"));
    }

    public static String resolveSpritePath(Map<String, String> materialPaths, String spriteKey) {
        return TexturePathPlanner.ensureSpritePath(materialPaths, spriteKey);
    }

    public static String resolveEntityLikePath(Map<String, String> materialPaths,
                                               String spriteKey,
                                               ExportOptions options) {
        return TexturePathResolver.ensureEntityLikePath(materialPaths, spriteKey, options);
    }
}
