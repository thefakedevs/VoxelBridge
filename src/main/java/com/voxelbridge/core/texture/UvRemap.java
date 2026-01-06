package com.voxelbridge.core.texture;

/**
 * Pure UV remapping utilities based on atlas placements.
 */
public final class UvRemap {
    private UvRemap() {}

    public static float[] remap(UvPlacement placement, float u, float v) {
        if (placement == null) {
            return new float[] {u, v};
        }
        double uu = placement.u0() + (double) u * (placement.u1() - placement.u0());
        double vv = placement.v0() + (double) v * (placement.v1() - placement.v0());
        return new float[] {(float) uu, (float) vv};
    }

    public static float[] remapFromPixels(UvPlacement placement, float uPx, float vPx, int width, int height) {
        float u = width > 0 ? uPx / (float) width : 0f;
        float v = height > 0 ? vPx / (float) height : 0f;
        return remap(placement, u, v);
    }
}
