package com.voxelbridge.adapter;

import com.voxelbridge.client.SelectionRenderer;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * 1.21.8 selection renderer using compat pipeline.
 */
public final class NeoForgeSelectionRenderBridge implements SelectionRenderBridge {

    @Override
    public void onRenderLevel(RenderLevelStageEvent event) {
        SelectionRenderer.onRenderLevel(event);
    }
}
