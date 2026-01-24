package com.voxelbridge.compat;

import com.voxelbridge.mixin.HttpTextureAccessor;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;

import java.io.File;

/**
 * Fabric-specific texture access using Mixin accessors.
 */
public final class FabricTextureAccess {

    private FabricTextureAccess() {
    }

    /**
     * Gets the file from an HttpTexture.
     */
    public static File getHttpTextureFile(AbstractTexture texture) {
        if (texture == null)
            return null;
        try {
            if (texture instanceof HttpTextureAccessor accessor) {
                return accessor.voxelbridge$getFile();
            }
        } catch (Throwable t) {
            // Not an HttpTexture or accessor failed
        }
        return null;
    }

    /**
     * Checks if a texture is an HttpTexture.
     */
    public static boolean isHttpTexture(AbstractTexture texture) {
        if (texture == null)
            return false;
        try {
            return texture instanceof HttpTextureAccessor;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Gets pixels from a DynamicTexture.
     */
    public static NativeImage getDynamicTexturePixels(AbstractTexture texture) {
        if (texture instanceof DynamicTexture) {
            try {
                return ((DynamicTexture) texture).getPixels();
            } catch (Throwable t) {
                // Fallback to public method
            }
        }
        return null;
    }
}
