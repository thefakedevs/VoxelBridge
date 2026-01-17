package com.voxelbridge.adapter;

import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Version bridge for selection overlay rendering.
 */
public interface SelectionRenderBridge {
    void onRenderLevel(RenderLevelStageEvent event);

    /**
     * Register selection renderer to the appropriate event on the game bus.
     */
    default void register(Object gameBus) {
        com.voxelbridge.platform.neoforge.NeoForgeEventBusBridge.addListener(gameBus, this::onRenderLevel);
    }
}
