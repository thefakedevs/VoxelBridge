package com.voxelbridge.compat;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;

import java.io.File;
import java.lang.reflect.Field;

public final class ForgeTextureAccess {
    private static final String[] HTTP_TEXTURE_FILE_FIELDS = {"file", "f_117994_"};

    private ForgeTextureAccess() {
    }

    public static File getHttpTextureFile(AbstractTexture texture) {
        if (texture == null) {
            return null;
        }

        Class<?> type = texture.getClass();
        while (type != null) {
            for (String fieldName : HTTP_TEXTURE_FILE_FIELDS) {
                try {
                    Field field = type.getDeclaredField(fieldName);
                    field.setAccessible(true);
                    Object value = field.get(texture);
                    if (value instanceof File file) {
                        return file;
                    }
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                }
            }
            type = type.getSuperclass();
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
