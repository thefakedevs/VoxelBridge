package com.voxelbridge.platform;

import com.voxelbridge.adapter.Adapters;
import com.voxelbridge.client.HudOverlayRenderer;
import com.voxelbridge.client.KeyBindings;
import com.voxelbridge.client.KeyInputHandler;
import com.voxelbridge.command.VoxelBridgeCommands;
import com.voxelbridge.config.ExportConfigStore;
import com.voxelbridge.export.ExportControl;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;

public final class ForgePlatformBootstrap implements PlatformBootstrap {
    private final IEventBus modBus;

    public ForgePlatformBootstrap(IEventBus modBus) {
        this.modBus = modBus;
    }

    @Override
    public void init() {
        IEventBus gameBus = MinecraftForge.EVENT_BUS;

        modBus.addListener((RegisterKeyMappingsEvent event) -> KeyBindings.register(event::register));
        modBus.addListener(this::registerGuiOverlays);
        gameBus.addListener((RegisterClientCommandsEvent event) -> VoxelBridgeCommands.register(event.getDispatcher()));
        gameBus.addListener((TickEvent.ClientTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END) {
                KeyInputHandler.onClientTick();
                ForgeConfigScreen.onClientTick();
            }
        });
        gameBus.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> ExportControl.clearSelection());

        Adapters.getSelectionRender().register(gameBus);

        com.voxelbridge.export.exporter.resolve.TextRenderTypeUtil.registerHint("forge_text");
        ExportConfigStore.init();
        ConfigScreenBridge.setOpener(mc -> ForgeConfigScreen.requestOpen(mc.screen));
        ModLoadingContext.get().registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> ForgeConfigScreen.create(parent)));
    }

    private void registerGuiOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "voxelbridge_progress",
                (gui, graphics, partialTick, width, height) -> HudOverlayRenderer.render(graphics));
    }
}
