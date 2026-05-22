package com.voxelbridge.mixin;

import net.minecraft.client.renderer.texture.DynamicTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.client.gui.MapRenderer$MapInstance")
public interface MapInstanceAccessor {
    @Accessor("texture")
    DynamicTexture voxelbridge$getTexture();
}
