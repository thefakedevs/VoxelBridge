package com.voxelbridge.platform;

import com.voxelbridge.config.ExportConfigStore;
import com.voxelbridge.config.ExportRuntimeConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class ForgeConfigScreen extends Screen {
    private static volatile boolean pendingOpen;
    private static volatile Screen pendingParent;

    private final Screen parent;

    private ForgeConfigScreen(Screen parent) {
        super(Component.translatable("config.voxelbridge.title"));
        this.parent = parent;
    }

    public static void requestOpen(Screen parent) {
        pendingParent = parent;
        pendingOpen = true;
    }

    public static void onClientTick() {
        if (!pendingOpen) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Screen parent = pendingParent != null ? pendingParent : mc.screen;
        pendingParent = null;
        pendingOpen = false;
        mc.setScreen(create(parent));
    }

    public static Screen create(Screen parent) {
        return new ForgeConfigScreen(parent);
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.literal("Toggle logging: " + onOff(ExportRuntimeConfig.isLoggingEnabled())), button -> {
            ExportRuntimeConfig.setLoggingEnabled(!ExportRuntimeConfig.isLoggingEnabled());
            ExportConfigStore.save();
            minecraft.setScreen(create(parent));
        }).bounds(width / 2 - 120, height / 2 - 40, 240, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Toggle animation: " + onOff(ExportRuntimeConfig.isAnimationEnabled())), button -> {
            ExportRuntimeConfig.setAnimationEnabled(!ExportRuntimeConfig.isAnimationEnabled());
            ExportConfigStore.save();
            minecraft.setScreen(create(parent));
        }).bounds(width / 2 - 120, height / 2 - 15, 240, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(width / 2 - 100, height / 2 + 35, 200, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, height / 2 - 75, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.literal("Full option editing is available via /voxelbridge commands."), width / 2, height / 2 - 60, 0xA0A0A0);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    private static String onOff(boolean value) {
        return value ? "ON" : "OFF";
    }
}
