package com.voxelbridge.mixin;

import net.minecraft.client.renderer.texture.DynamicTexture;
import com.mojang.blaze3d.platform.NativeImage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Mixin accessor for DynamicTexture private fields.
 */
@Mixin(DynamicTexture.class)
public interface DynamicTextureAccessor {

    @Accessor("pixels")
    NativeImage voxelbridge$getPixels();
}
