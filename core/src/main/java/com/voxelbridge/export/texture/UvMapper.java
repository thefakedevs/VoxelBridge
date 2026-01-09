package com.voxelbridge.export.texture;

import com.voxelbridge.core.export.ExportState;

/**
 * Core UV remapping API, driven by ExportOptions and ExportState placements.
 */
public final class UvMapper {

    private UvMapper() {}

    public static float[] remapUv(ExportState state, String spriteKey, float u, float v, ExportOptions options) {
        return UvRemapUtil.remapUv(state, spriteKey, u, v, options);
    }

    public static float[] remapUv(ExportState state, String spriteKey, int tint, float u, float v, ExportOptions options) {
        return UvRemapUtil.remapUv(state, spriteKey, tint, u, v, options);
    }

    public static float[] remapUvFromPixels(ExportState state, String spriteKey,
                                            float uPx, float vPx, int width, int height, ExportOptions options) {
        return UvRemapUtil.remapUvFromPixels(state, spriteKey, uPx, vPx, width, height, options);
    }
}
