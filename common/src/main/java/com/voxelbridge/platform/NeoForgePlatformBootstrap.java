package com.voxelbridge.platform;

import com.voxelbridge.client.HudOverlayRenderer;
import com.voxelbridge.client.KeyBindings;
import com.voxelbridge.client.KeyInputHandler;
import com.voxelbridge.adapter.Adapters;
import com.voxelbridge.command.VoxelBridgeCommands;
import com.voxelbridge.platform.neoforge.NeoForgeEventBusBridge;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;

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

        Object gameBus = NeoForgeEventBusBridge.resolveGameBus(modBus);
        NeoForgeEventBusBridge.addListener(modBus, KeyBindings::onRegisterKeyMappings);
        NeoForgeEventBusBridge.addListener(gameBus, VoxelBridgeCommands::register);
        NeoForgeEventBusBridge.addListener(gameBus, KeyInputHandler::onClientTick);
        Adapters.getSelectionRender().register(gameBus);
        NeoForgeEventBusBridge.addListener(gameBus, HudOverlayRenderer::onRenderGui);
    }
}
