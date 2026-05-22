package com.voxelbridge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.io.File;

@Mixin(net.minecraft.client.renderer.texture.HttpTexture.class)
public interface HttpTextureAccessor {
    @Accessor("file")
    File voxelbridge$getFile();
}
