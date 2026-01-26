package com.voxelbridge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.io.File;

/**
 * Mixin accessor for HttpTexture fields.
 * HttpTexture is a package-private class in Minecraft.
 */
@Pseudo
@Mixin(targets = "net.minecraft.client.renderer.texture.HttpTexture")
public interface HttpTextureAccessor {

    @Accessor("file")
    File voxelbridge$getFile();
}
