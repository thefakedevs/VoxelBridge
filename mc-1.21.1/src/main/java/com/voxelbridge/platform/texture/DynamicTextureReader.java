package com.voxelbridge.platform.texture;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.voxelbridge.platform.client.ClientAccessHolder;
import com.voxelbridge.util.debug.LogModule;
import com.voxelbridge.util.debug.VoxelBridgeLogger;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.HttpTexture;
import net.minecraft.resources.ResourceLocation;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Attempts to read runtime textures from TextureManager when no resource exists.
 */
final class DynamicTextureReader {

    private DynamicTextureReader() {}

    static BufferedImage tryRead(ResourceLocation location) {
        boolean logDynamicMap = isDynamicMapLocation(location);
        try {
            AbstractTexture texture = ClientAccessHolder.get().getTextureManager().getTexture(location);
            if (texture == null) {
                ResourceLocation fallback = resolveDynamicLocation(location);
                if (fallback != null && !fallback.equals(location)) {
                    if (logDynamicMap) {
                        VoxelBridgeLogger.info(LogModule.DYNAMIC_MAP,
                            "[DynamicMap] Missing texture, trying fallback key: " + fallback);
                    }
                    texture = ClientAccessHolder.get().getTextureManager().getTexture(fallback);
                }
            }
            if (texture == null) {
                if (logDynamicMap) {
                    VoxelBridgeLogger.warn(LogModule.DYNAMIC_MAP,
                        "[DynamicMap] TextureManager has no entry for " + location);
                }
                return null;
            }
            BufferedImage best = null;
            BufferedImage fromDynamic = readDynamicTexture(texture);
            if (fromDynamic != null) {
                if (logDynamicMap) {
                    VoxelBridgeLogger.info(LogModule.DYNAMIC_MAP,
                        "[DynamicMap] Loaded via DynamicTexture (" + fromDynamic.getWidth() + "x" + fromDynamic.getHeight() + ")");
                }
                if (!logDynamicMap) {
                    return fromDynamic;
                }
                best = preferLarger(best, fromDynamic);
            }
            BufferedImage fromNative = readNativeImageTexture(texture);
            if (fromNative != null) {
                if (logDynamicMap) {
                    VoxelBridgeLogger.info(LogModule.DYNAMIC_MAP,
                        "[DynamicMap] Loaded via NativeImage (" + fromNative.getWidth() + "x" + fromNative.getHeight() + ")");
                }
                if (!logDynamicMap) {
                    return fromNative;
                }
                best = preferLarger(best, fromNative);
            }
            BufferedImage fromHttp = readHttpTexture(texture);
            if (fromHttp != null) {
                if (logDynamicMap) {
                    VoxelBridgeLogger.info(LogModule.DYNAMIC_MAP,
                        "[DynamicMap] Loaded via HttpTexture (" + fromHttp.getWidth() + "x" + fromHttp.getHeight() + ")");
                }
                if (!logDynamicMap) {
                    return fromHttp;
                }
                best = preferLarger(best, fromHttp);
            }
            BufferedImage fromGpu = readGpuTexture(texture, logDynamicMap);
            if (fromGpu != null) {
                if (logDynamicMap) {
                    VoxelBridgeLogger.info(LogModule.DYNAMIC_MAP,
                        "[DynamicMap] Loaded via GPU readback (" + fromGpu.getWidth() + "x" + fromGpu.getHeight() + ")");
                }
                if (!logDynamicMap) {
                    return fromGpu;
                }
                best = preferLarger(best, fromGpu);
            }
            if (logDynamicMap) {
                return best;
            }
        } catch (Throwable t) {
            VoxelBridgeLogger.warn(LogModule.TEXTURE_RESOLVE, String.format("[DynamicTextureReader][WARN] Failed to read %s: %s", location, t.getMessage()));
        }
        if (logDynamicMap) {
            VoxelBridgeLogger.warn(LogModule.DYNAMIC_MAP, "[DynamicMap] Read failed for " + location);
        }
        return null;
    }

    private static ResourceLocation resolveDynamicLocation(ResourceLocation location) {
        if (location == null) {
            return null;
        }
        String path = location.getPath();
        String normalized = path;
        if (normalized.startsWith("textures/")) {
            normalized = normalized.substring("textures/".length());
        }
        if (!normalized.startsWith("dynamic/")) {
            return null;
        }
        if (normalized.endsWith(".png")) {
            normalized = normalized.substring(0, normalized.length() - 4);
        }
        if (normalized.equals(path)) {
            return null;
        }
        return ResourceLocation.fromNamespaceAndPath(location.getNamespace(), normalized);
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

    private static BufferedImage preferLarger(BufferedImage current, BufferedImage candidate) {
        if (candidate == null) {
            return current;
        }
        if (current == null) {
            return candidate;
        }
        int currentArea = current.getWidth() * current.getHeight();
        int candidateArea = candidate.getWidth() * candidate.getHeight();
        return candidateArea >= currentArea ? candidate : current;
    }

    private static BufferedImage readHttpTexture(AbstractTexture texture) {
        if (!(texture instanceof HttpTexture)) {
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

    private static BufferedImage readGpuTexture(AbstractTexture texture, boolean logDynamicMap) {
        if (!RenderSystem.isOnRenderThreadOrInit()) {
            if (logDynamicMap) {
                VoxelBridgeLogger.warn(LogModule.DYNAMIC_MAP, "[DynamicMap] GPU read skipped: not on render thread");
            }
            return null;
        }
        try {
            int id = texture.getId();
            if (id <= 0) {
                if (logDynamicMap) {
                    VoxelBridgeLogger.warn(LogModule.DYNAMIC_MAP, "[DynamicMap] GPU read skipped: invalid texture id");
                }
                return null;
            }
            Method download = NativeImage.class.getDeclaredMethod("downloadTexture", int.class, boolean.class);
            download.setAccessible(true);
            if (Modifier.isStatic(download.getModifiers())) {
                Object value = download.invoke(null, id, false);
                if (value instanceof NativeImage nativeImg) {
                    return TextureLoader.fromNativeImage(nativeImg);
                }
                return null;
            }
            NativeImage target = resolveNativeImageTarget(texture);
            if (target == null) {
                if (logDynamicMap) {
                    VoxelBridgeLogger.warn(LogModule.DYNAMIC_MAP, "[DynamicMap] GPU read skipped: no NativeImage target");
                }
                return null;
            }
            download.invoke(target, id, false);
            return TextureLoader.fromNativeImage(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean isDynamicMapLocation(ResourceLocation location) {
        if (location == null) {
            return false;
        }
        String path = location.getPath();
        return path.startsWith("dynamic/map/")
            || path.startsWith("textures/dynamic/map/");
    }

    private static NativeImage resolveNativeImageTarget(AbstractTexture texture) {
        if (texture instanceof DynamicTexture dynamic) {
            return dynamic.getPixels();
        }
        NativeImage nativeImg = findNativeImage(texture);
        if (nativeImg != null) {
            return nativeImg;
        }
        int[] size = resolveTextureSize(texture);
        if (size == null) {
            return null;
        }
        try {
            return new NativeImage(size[0], size[1], false);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static int[] resolveTextureSize(AbstractTexture texture) {
        Integer width = tryGetInt(texture, "getWidth");
        Integer height = tryGetInt(texture, "getHeight");
        if (width == null || height == null) {
            width = tryGetIntField(texture, "width");
            height = tryGetIntField(texture, "height");
        }
        if (width == null || height == null || width <= 0 || height <= 0) {
            return null;
        }
        return new int[] {width, height};
    }

    private static Integer tryGetInt(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            Object value = method.invoke(target);
            if (value instanceof Integer i) {
                return i;
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return null;
    }

    private static Integer tryGetIntField(Object target, String fieldName) {
        Class<?> type = target.getClass();
        while (type != null && type != Object.class) {
            try {
                Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                Object value = field.get(target);
                if (value instanceof Integer i) {
                    return i;
                }
            } catch (ReflectiveOperationException ignored) {
            }
            type = type.getSuperclass();
        }
        return null;
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



