package com.voxelbridge.compat;

import com.mojang.blaze3d.platform.NativeImage;
import com.voxelbridge.mixin.HttpTextureAccessor;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;

import java.io.File;

public final class ForgeTextureAccess {
    private ForgeTextureAccess() {
    }

    public static File getHttpTextureFile(AbstractTexture texture) {
        if (texture == null) {
            return null;
        }
        try {
            if (texture instanceof HttpTextureAccessor accessor) {
                return accessor.voxelbridge$getFile();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    public static NativeImage getDynamicTexturePixels(AbstractTexture texture) {
        if (texture instanceof DynamicTexture dynamicTexture) {
            try {
                return dynamicTexture.getPixels();
            } catch (Throwable ignored) {
            }
        }
        return null;
    }
}
