package com.voxelbridge.core.util.color;

/**
 * Abstraction for colormap access from core code.
 */
public interface ColorMapAccess {
    ColorMapUv registerColor(int argb);
}
