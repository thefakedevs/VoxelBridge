package com.voxelbridge.mixin;

import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.resources.model.ModelManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.io.File;

/**
 * Mixin accessor for HttpTexture fields.
 * HttpTexture is a package-private class in Minecraft.
 */
@Mixin(net.minecraft.client.renderer.texture.HttpTexture.class)
public interface HttpTextureAccessor {

    @Accessor("file")
    File voxelbridge$getFile();
}
