package com.voxelbridge.compat;

import com.voxelbridge.mixin.TextureAtlasHolderAccessor;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.PaintingTextureManager;
import net.minecraft.resources.ResourceLocation;

/**
 * Fabric-specific painting texture access using Mixin accessors.
 */
public final class FabricPaintingAccess {

    private FabricPaintingAccess() {
    }

    /**
     * Gets the back sprite from PaintingTextureManager.
     */
    public static TextureAtlasSprite getBackSprite(Object paintingManager) {
        if (paintingManager instanceof PaintingTextureManager manager) {
            try {
                return manager.getBackSprite();
            } catch (Throwable t) {
                // Fallback
            }
        }
        return null;
    }

    /**
     * Gets a sprite from TextureAtlasHolder (parent of PaintingTextureManager).
     */
    public static TextureAtlasSprite getSprite(Object atlasHolder, ResourceLocation location) {
        if (atlasHolder instanceof TextureAtlasHolderAccessor accessor) {
            try {
                return accessor.voxelbridge$getSprite(location);
            } catch (Throwable t) {
                // Fallback
            }
        }
        return null;
    }

    /**
     * Gets the TextureAtlas from a TextureAtlasHolder.
     */
    public static TextureAtlas getTextureAtlas(Object atlasHolder) {
        if (atlasHolder instanceof TextureAtlasHolderAccessor accessor) {
            try {
                return accessor.voxelbridge$getTextureAtlas();
            } catch (Throwable t) {
                // Fallback
            }
        }
        return null;
    }
}
