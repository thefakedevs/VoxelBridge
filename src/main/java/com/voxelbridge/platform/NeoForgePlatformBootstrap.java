package com.voxelbridge.platform;

import com.voxelbridge.client.HudOverlayRenderer;
import com.voxelbridge.client.KeyBindings;
import com.voxelbridge.client.KeyInputHandler;
import com.voxelbridge.client.SelectionRenderer;
import com.voxelbridge.command.VoxelBridgeCommands;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;

/**
 * NeoForge event registration for client-only hooks.
 */
public final class NeoForgePlatformBootstrap implements PlatformBootstrap {
    
    private final Dist dist;
    private final IEventBus modBus;

    public NeoForgePlatformBootstrap(Dist dist, IEventBus modBus) {
        this.dist = dist;
        this.modBus = modBus;
    }

    @Override
    public void init() {
        if (dist != Dist.CLIENT) {
            return;
        }

        modBus.addListener(KeyBindings::onRegisterKeyMappings);
        NeoForge.EVENT_BUS.addListener(VoxelBridgeCommands::register);
        NeoForge.EVENT_BUS.addListener(KeyInputHandler::onClientTick);
        NeoForge.EVENT_BUS.addListener(SelectionRenderer::onRenderLevel);
        NeoForge.EVENT_BUS.addListener(HudOverlayRenderer::onRenderGui);
    }
}