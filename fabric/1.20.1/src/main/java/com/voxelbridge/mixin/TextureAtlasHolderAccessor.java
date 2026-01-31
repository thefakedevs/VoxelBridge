package com.voxelbridge.mixin;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.TextureAtlasHolder;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Mixin accessor for TextureAtlasHolder (parent of PaintingTextureManager).
 */
@Mixin(TextureAtlasHolder.class)
public interface TextureAtlasHolderAccessor {

    @Invoker("getSprite")
    TextureAtlasSprite voxelbridge$getSprite(ResourceLocation location);

    @Accessor("textureAtlas")
    net.minecraft.client.renderer.texture.TextureAtlas voxelbridge$getTextureAtlas();
}
