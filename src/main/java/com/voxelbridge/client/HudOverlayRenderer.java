package com.voxelbridge.client;

import com.voxelbridge.util.client.ProgressNotifier;
import com.voxelbridge.platform.client.ClientAccessHolder;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

public final class HudOverlayRenderer {

    private HudOverlayRenderer() {}

    public static void onRenderGui(RenderGuiEvent.Post event) {
        var mc = ClientAccessHolder.get().getMinecraft();
        ProgressNotifier.renderOverlay(mc, event.getGuiGraphics());
    }
}
