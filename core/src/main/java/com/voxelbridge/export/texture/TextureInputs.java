package com.voxelbridge.export.texture;

import com.voxelbridge.core.texture.AnimatedFrameSet;

import java.awt.image.BufferedImage;
import java.util.Map;

/**
 * Core-facing texture inputs provided by common/platform layers.
 */
public record TextureInputs(
    Map<String, BufferedImage> baseSprites,
    Map<String, BufferedImage> normalSprites,
    Map<String, BufferedImage> specSprites,
    Map<String, AnimatedFrameSet> animations,
    Map<String, String> originalMcmeta
) {}
