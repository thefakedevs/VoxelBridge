package com.voxelbridge.platform.texture;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.voxelbridge.platform.client.ClientAccessHolder;
import com.voxelbridge.util.debug.LogModule;
import com.voxelbridge.util.debug.VoxelBridgeLogger;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.ref.SoftReference;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Attempts to read runtime textures from TextureManager when no resource exists.
 */
final class DynamicTextureReader {

    private DynamicTextureReader() {}

    private static final ConcurrentHashMap<ResourceLocation, SoftReference<BufferedImage>> CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<ResourceLocation, CompletableFuture<BufferedImage>> IN_FLIGHT = new ConcurrentHashMap<>();

    static BufferedImage tryRead(ResourceLocation location) {
        SoftReference<BufferedImage> cachedRef = CACHE.get(location);
        if (cachedRef != null) {
            BufferedImage cached = cachedRef.get();
            if (cached != null) {
                return cached;
            }
        }
        if (RenderSystem.isOnRenderThreadOrInit()) {
            return readOnRenderThread(location);
        }
        CompletableFuture<BufferedImage> future = IN_FLIGHT.computeIfAbsent(location, loc -> {
            CompletableFuture<BufferedImage> created = new CompletableFuture<>();
            RenderSystem.recordRenderCall(() -> {
                try {
                    BufferedImage img = readOnRenderThread(loc);
                    created.complete(img);
                } catch (Throwable t) {
                    created.completeExceptionally(t);
                } finally {
                    IN_FLIGHT.remove(loc);
                }
            });
            return created;
        });
        try {
            return future.get();
        } catch (Exception e) {
            VoxelBridgeLogger.warn(LogModule.TEXTURE_RESOLVE, String.format("[DynamicTextureReader][WARN] Failed to read %s: %s", location, e.getMessage()));
            return null;
        }
    }

    private static BufferedImage readOnRenderThread(ResourceLocation location) {
        try {
            AbstractTexture texture = ClientAccessHolder.get().getTextureManager().getTexture(location);
            if (texture == null) {
                return null;
            }
            BufferedImage fromDynamic = readDynamicTexture(texture);
            if (fromDynamic != null) {
                CACHE.put(location, new SoftReference<>(fromDynamic));
                return fromDynamic;
            }
            BufferedImage fromNative = readNativeImageTexture(texture);
            if (fromNative != null) {
                CACHE.put(location, new SoftReference<>(fromNative));
                return fromNative;
            }
            BufferedImage fromHttp = readHttpTexture(texture);
            if (fromHttp != null) {
                CACHE.put(location, new SoftReference<>(fromHttp));
                return fromHttp;
            }
        } catch (Throwable t) {
            VoxelBridgeLogger.warn(LogModule.TEXTURE_RESOLVE, String.format("[DynamicTextureReader][WARN] Failed to read %s: %s", location, t.getMessage()));
        }
        return null;
    }

    private static BufferedImage readDynamicTexture(AbstractTexture texture) {
        if (texture instanceof DynamicTexture dynamic) {
            var pixels = dynamic.getPixels();
            if (pixels != null) {
                return TextureLoader.fromNativeImage(pixels);
            }
        }
        return null;
    }

    private static BufferedImage readHttpTexture(AbstractTexture texture) {
        if (!isInstance(texture, "net.minecraft.client.renderer.texture.HttpTexture")) {
            return null;
        }
        File file = findFileField(texture);
        if (file == null || !file.isFile()) {
            return null;
        }
        try {
            return ImageIO.read(file);
        } catch (Exception e) {
            VoxelBridgeLogger.warn(LogModule.TEXTURE_RESOLVE, String.format("[DynamicTextureReader][WARN] Failed to read HttpTexture file %s: %s", file, e.getMessage()));
            return null;
        }
    }

    private static BufferedImage readNativeImageTexture(AbstractTexture texture) {
        NativeImage nativeImg = findNativeImage(texture);
        if (nativeImg == null) {
            nativeImg = invokeNativeImageMethod(texture);
        }
        if (nativeImg == null) {
            return null;
        }
        return TextureLoader.fromNativeImage(nativeImg);
    }

    private static NativeImage findNativeImage(AbstractTexture texture) {
        Class<?> type = texture.getClass();
        while (type != null && type != Object.class) {
            for (Field field : type.getDeclaredFields()) {
                if (!NativeImage.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    Object value = field.get(texture);
                    if (value instanceof NativeImage nativeImg) {
                        return nativeImg;
                    }
                } catch (IllegalAccessException ignored) {
                    return null;
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }

    private static NativeImage invokeNativeImageMethod(AbstractTexture texture) {
        Class<?> type = texture.getClass();
        while (type != null && type != Object.class) {
            for (Method method : type.getDeclaredMethods()) {
                if (method.getParameterCount() != 0) {
                    continue;
                }
                if (!NativeImage.class.isAssignableFrom(method.getReturnType())) {
                    continue;
                }
                try {
                    method.setAccessible(true);
                    Object value = method.invoke(texture);
                    if (value instanceof NativeImage nativeImg) {
                        return nativeImg;
                    }
                } catch (ReflectiveOperationException ignored) {
                    return null;
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }

    private static boolean isInstance(AbstractTexture texture, String className) {
        try {
            Class<?> clazz = Class.forName(className);
            return clazz.isInstance(texture);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static File findFileField(AbstractTexture texture) {
        for (String name : new String[] {"file", "cacheFile", "path"}) {
            File f = getFileField(texture, name);
            if (f != null) {
                return f;
            }
        }
        for (Field field : texture.getClass().getDeclaredFields()) {
            if (File.class.isAssignableFrom(field.getType())) {
                File f = getFileField(texture, field.getName());
                if (f != null) {
                    return f;
                }
            }
        }
        return null;
    }

    private static File getFileField(AbstractTexture texture, String name) {
        try {
            Field field = texture.getClass().getDeclaredField(name);
            field.setAccessible(true);
            Object value = field.get(texture);
            if (value instanceof File) {
                return (File) value;
            }
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
        return null;
    }
}



