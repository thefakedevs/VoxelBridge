package com.voxelbridge.core.ir;

/**
 * Packed quad flags for IR transport and storage.
 * Bit layout:
 * 0: double-sided
 * 1: emissive
 * 2-3: render layer (ordinal)
 * 4-5: tint mode (ordinal)
 */
public final class IrFlags {
    public static final int DOUBLE_SIDED = 1;
    public static final int EMISSIVE = 1 << 1;
    private static final int RENDER_LAYER_SHIFT = 2;
    private static final int TINT_MODE_SHIFT = 4;
    private static final int RENDER_LAYER_MASK = 0x3 << RENDER_LAYER_SHIFT;
    private static final int TINT_MODE_MASK = 0x3 << TINT_MODE_SHIFT;

    private IrFlags() {}

    public static int encode(RenderLayer renderLayer, TintMode tintMode, boolean doubleSided, boolean emissive) {
        int flags = 0;
        if (doubleSided) flags |= DOUBLE_SIDED;
        if (emissive) flags |= EMISSIVE;
        int layer = renderLayer != null ? renderLayer.ordinal() : RenderLayer.UNKNOWN.ordinal();
        int tint = tintMode != null ? tintMode.ordinal() : TintMode.UNKNOWN.ordinal();
        flags |= (layer << RENDER_LAYER_SHIFT) & RENDER_LAYER_MASK;
        flags |= (tint << TINT_MODE_SHIFT) & TINT_MODE_MASK;
        return flags;
    }

    public static boolean isDoubleSided(int flags) {
        return (flags & DOUBLE_SIDED) != 0;
    }

    public static boolean isEmissive(int flags) {
        return (flags & EMISSIVE) != 0;
    }

    public static RenderLayer decodeRenderLayer(int flags) {
        int idx = (flags & RENDER_LAYER_MASK) >> RENDER_LAYER_SHIFT;
        RenderLayer[] values = RenderLayer.values();
        return idx >= 0 && idx < values.length ? values[idx] : RenderLayer.UNKNOWN;
    }

    public static TintMode decodeTintMode(int flags) {
        int idx = (flags & TINT_MODE_MASK) >> TINT_MODE_SHIFT;
        TintMode[] values = TintMode.values();
        return idx >= 0 && idx < values.length ? values[idx] : TintMode.UNKNOWN;
    }
}
