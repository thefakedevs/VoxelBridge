package com.voxelbridge.core.util.image;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * Image helpers that are independent of Minecraft APIs.
 */
public final class ImageUtil {

    private ImageUtil() {}

    public static BufferedImage copy(BufferedImage src) {
        if (src == null) {
            return null;
        }
        BufferedImage copy = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = copy.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return copy;
    }

    public static BufferedImage copyOrBlank(BufferedImage src, int width, int height) {
        if (src == null) {
            return new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        }
        return copy(src);
    }

    public static BufferedImage scaleNearest(BufferedImage src, int width, int height) {
        if (src == null) {
            return null;
        }
        if (src.getWidth() == width && src.getHeight() == height) {
            return src;
        }
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(src, 0, 0, width, height, null);
        g.dispose();
        return out;
    }

    public static int clampChannel(int c) {
        return Math.max(0, Math.min(255, c));
    }

    public static int alphaBlend(int dst, int src) {
        int srcA = src >>> 24 & 0xFF;
        int dstA = dst >>> 24 & 0xFF;
        int outA = srcA + dstA * (255 - srcA) / 255;
        if (outA == 0) {
            return 0;
        }
        int srcR = src >>> 16 & 0xFF;
        int srcG = src >>> 8 & 0xFF;
        int srcB = src & 0xFF;
        int dstR = dst >>> 16 & 0xFF;
        int dstG = dst >>> 8 & 0xFF;
        int dstB = dst & 0xFF;
        int outR = (srcR * srcA + dstR * dstA * (255 - srcA) / 255) / outA;
        int outG = (srcG * srcA + dstG * dstA * (255 - srcA) / 255) / outA;
        int outB = (srcB * srcA + dstB * dstA * (255 - srcA) / 255) / outA;
        return outA << 24 | outR << 16 | outG << 8 | outB;
    }
}
