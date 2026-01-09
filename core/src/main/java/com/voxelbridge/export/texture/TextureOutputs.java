package com.voxelbridge.export.texture;

import com.voxelbridge.core.export.ExportState;

import java.util.Map;
import java.util.Set;

/**
 * Core-facing texture outputs (placements, paths) for downstream consumers.
 */
public record TextureOutputs(
    Map<String, ExportState.TexturePlacement> placements,
    Map<String, String> materialPaths,
    Set<String> exportedSprites
) {}
