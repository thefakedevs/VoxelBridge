package com.voxelbridge.export.texture;

public final class TexturePathResolver {

    private TexturePathResolver() {}

    private static boolean isIndividualMode(ExportOptions options) {
        return options != null && options.atlasMode() == ExportOptions.AtlasMode.INDIVIDUAL;
    }

    public static String entityTextureDir(ExportOptions options) {
        return isIndividualMode(options) ? "textures/individual" : "entity_textures";
    }

    public static String ensureEntityLikePath(java.util.Map<String, String> materialPaths,
                                              String spriteKey,
                                              ExportOptions options) {
        String existing = materialPaths.get(spriteKey);
        if (existing != null) {
            return existing;
        }
        String rel = entityTextureDir(options) + "/" + safe(spriteKey) + ".png";
        materialPaths.put(spriteKey, rel);
        return rel;
    }

    public static String ensureGeneratedPath(java.util.Map<String, String> materialPaths,
                                             String spriteKey,
                                             ExportOptions options) {
        String existing = materialPaths.get(spriteKey);
        if (existing != null) {
            return existing;
        }
        String rel = isIndividualMode(options)
            ? entityTextureDir(options) + "/" + safe(spriteKey) + ".png"
            : "entity_textures/generated/" + safe(spriteKey) + ".png";
        materialPaths.put(spriteKey, rel);
        return rel;
    }

    public static String entityPbrPath(ExportOptions options, String spriteKey, String suffix) {
        return entityTextureDir(options) + "/" + safe(spriteKey) + suffix + ".png";
    }

    public static String safe(String spriteKey) {
        String s = spriteKey.replace(':', '_').replace('/', '_');
        if (s.length() > 96) {
            return s.substring(0, 80) + "__" + Integer.toHexString(s.hashCode());
        }
        return s;
    }
}
