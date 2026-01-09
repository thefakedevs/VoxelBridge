package com.voxelbridge.export.texture;

import com.voxelbridge.config.ExportRuntimeConfig;
import com.voxelbridge.core.util.color.ColorMode;

/**
 * Core-facing export options for texture/UV processing.
 */
public record ExportOptions(
    AtlasMode atlasMode,
    int atlasSize,
    int atlasPadding,
    ColorMode colorMode,
    boolean animationEnabled,
    boolean pbrDecodeEnabled
) {
    public static ExportOptions fromRuntimeConfig() {
        return new ExportOptions(
            ExportRuntimeConfig.getAtlasMode() == ExportRuntimeConfig.AtlasMode.ATLAS
                ? AtlasMode.ATLAS
                : AtlasMode.INDIVIDUAL,
            ExportRuntimeConfig.getAtlasSize().getSize(),
            ExportRuntimeConfig.getAtlasPadding(),
            ExportRuntimeConfig.getColorMode(),
            ExportRuntimeConfig.isAnimationEnabled(),
            ExportRuntimeConfig.isPbrDecodeEnabled()
        );
    }

    public enum AtlasMode {
        INDIVIDUAL,
        ATLAS
    }
}
