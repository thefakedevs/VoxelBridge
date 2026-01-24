package com.voxelbridge.platform;

import com.voxelbridge.client.HudOverlayRenderer;
import com.voxelbridge.client.KeyBindings;
import com.voxelbridge.client.KeyInputHandler;
import com.voxelbridge.adapter.Adapters;
import com.voxelbridge.command.VoxelBridgeCommands;
import com.voxelbridge.export.ExportControl;
import com.voxelbridge.platform.neoforge.NeoForgeEventBusBridge;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

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
        NeoForgeEventBusBridge.addListener(modBus, (RegisterKeyMappingsEvent event) -> KeyBindings.register(event::register));
        NeoForgeEventBusBridge.addListener(gameBus, (RegisterClientCommandsEvent event) -> VoxelBridgeCommands.register(event.getDispatcher()));
        NeoForgeEventBusBridge.addListener(gameBus, (ClientTickEvent.Post event) -> KeyInputHandler.onClientTick());
        NeoForgeEventBusBridge.addListener(gameBus, (ClientPlayerNetworkEvent.LoggingOut event) -> ExportControl.resetAll());
        Adapters.getSelectionRender().register(gameBus);
        NeoForgeEventBusBridge.addListener(gameBus, (RenderGuiEvent.Post event) -> HudOverlayRenderer.render(event.getGuiGraphics()));
    }
}
