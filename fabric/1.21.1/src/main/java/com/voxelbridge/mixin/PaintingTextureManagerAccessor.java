package com.voxelbridge.mixin;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.PaintingTextureManager;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Map;

/**
 * Mixin accessor for PaintingTextureManager.
 */
@Mixin(PaintingTextureManager.class)
public interface PaintingTextureManagerAccessor {

    @Invoker("getBackSprite")
    TextureAtlasSprite voxelbridge$getBackSprite();
}
