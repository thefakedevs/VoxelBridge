package com.voxelbridge.adapter;

import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Version bridge for selection overlay rendering.
 */
public interface SelectionRenderBridge {
    void onRenderLevel(RenderLevelStageEvent event);
}
