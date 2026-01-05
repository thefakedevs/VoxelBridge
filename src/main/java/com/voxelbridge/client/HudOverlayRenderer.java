package com.voxelbridge.client;

import com.voxelbridge.util.client.ProgressNotifier;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

public final class HudOverlayRenderer {

    private HudOverlayRenderer() {}

    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ProgressNotifier.renderOverlay(mc, event.getGuiGraphics());
    }
}
