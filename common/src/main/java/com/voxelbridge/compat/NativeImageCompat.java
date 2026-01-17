package com.voxelbridge.compat;

import com.mojang.blaze3d.platform.NativeImage;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Version-agnostic helpers for NativeImage API drift.
 */
public final class NativeImageCompat {

    private static final MethodHandles.Lookup LOOKUP = MethodHandles.publicLookup();
    private static final MethodHandle GET_PIXEL_RGBA = findHandle(int.class, "getPixelRGBA", int.class, int.class);
    private static final MethodHandle GET_PIXEL = findHandle(int.class, "getPixel", int.class, int.class);

    private NativeImageCompat() {}

    public static int getPixelRgba(NativeImage img, int x, int y) {
        if (img == null) {
            return 0;
        }
        Object value = invokeHandle(GET_PIXEL_RGBA, img, x, y);
        if (value instanceof Integer i) {
            return i;
        }
        value = invokeHandle(GET_PIXEL, img, x, y);
        if (value instanceof Integer i) {
            return argbToAbgr(i);
        }
        return 0;
    }

    private static int argbToAbgr(int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;
        return (a << 24) | (b << 16) | (g << 8) | r;
    }

    private static MethodHandle findHandle(Class<?> returnType, String name, Class<?>... params) {
        try {
            return LOOKUP.findVirtual(NativeImage.class, name, MethodType.methodType(returnType, params));
        } catch (NoSuchMethodException | IllegalAccessException ignored) {
            return null;
        }
    }

    private static Object invokeHandle(MethodHandle handle, Object target, Object... args) {
        if (handle == null) {
            return null;
        }
        try {
            return handle.invokeWithArguments(prepend(target, args));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object[] prepend(Object target, Object[] args) {
        Object[] out = new Object[args.length + 1];
        out[0] = target;
        System.arraycopy(args, 0, out, 1, args.length);
        return out;
    }
}
