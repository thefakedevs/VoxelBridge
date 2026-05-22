package com.voxelbridge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(targets = "net.minecraft.client.gui.MapRenderer$MapInstance")
public interface MapInstanceInvoker {
    @Invoker("forceUpload")
    void voxelbridge$forceUpload();
}
