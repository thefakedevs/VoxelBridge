package com.voxelbridge.core.util.image;

import java.awt.image.BufferedImage;

/**
 * Image sampling helpers that are independent of Minecraft APIs.
 */
public final class ImageSampling {

    private ImageSampling() {}

    /**
     * Computes integer pixel bounds for normalized UVs.
     *
     * @param uvBounds [minU, maxU, minV, maxV]
     * @param width image width
     * @param height image height
     * @param out [minX, maxX, minY, maxY]
     */
    public static boolean computePixelBounds(float[] uvBounds, int width, int height, int[] out) {
        if (uvBounds == null || uvBounds.length < 4 || out == null || out.length < 4) {
            return false;
        }
        if (width <= 0 || height <= 0) {
            return false;
        }
        float umin = uvBounds[0];
        float umax = uvBounds[1];
        float vmin = uvBounds[2];
        float vmax = uvBounds[3];
        int minX = Math.max(0, (int) Math.floor(umin * (width - 1)));
        int maxX = Math.min(width - 1, (int) Math.ceil(umax * (width - 1)));
        int minY = Math.max(0, (int) Math.floor(vmin * (height - 1)));
        int maxY = Math.min(height - 1, (int) Math.ceil(vmax * (height - 1)));
        out[0] = minX;
        out[1] = maxX;
        out[2] = minY;
        out[3] = maxY;
        return true;
    }

    /**
     * Returns true if all pixels in the region are fully transparent (alpha == 0).
     */
    public static boolean isRegionFullyTransparent(BufferedImage img, int minX, int maxX, int minY, int maxY) {
        if (img == null) {
            return false;
        }
        int width = img.getWidth();
        int height = img.getHeight();
        if (width <= 0 || height <= 0) {
            return false;
        }
        minX = Math.max(0, minX);
        minY = Math.max(0, minY);
        maxX = Math.min(width - 1, maxX);
        maxY = Math.min(height - 1, maxY);
        if (minX > maxX || minY > maxY) {
            return false;
        }
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                int alpha = (img.getRGB(x, y) >>> 24) & 0xFF;
                if (alpha != 0) {
                    return false;
                }
            }
        }
        return true;
    }
}
