package com.voxelbridge.adapter;

import net.minecraft.client.renderer.block.model.BakedQuad;

import java.util.Collections;
import java.util.List;

/**
 * Quad batch with source metadata for downstream routing.
 */
public record QuadBatch(List<BakedQuad> quads, QuadSource source) {
    public QuadBatch {
        quads = quads != null ? quads : Collections.emptyList();
        source = source != null ? source : QuadSource.UNKNOWN;
    }
}
