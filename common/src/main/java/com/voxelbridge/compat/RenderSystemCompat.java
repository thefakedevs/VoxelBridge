package com.voxelbridge.compat;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Version-agnostic helpers for RenderSystem API drift.
 */
public final class RenderSystemCompat {

    private static final MethodHandles.Lookup LOOKUP = MethodHandles.publicLookup();

    private static final MethodHandle IS_ON_RENDER_THREAD = findHandle(boolean.class,
        "isOnRenderThreadOrInit");
    private static final MethodHandle IS_ON_RENDER_THREAD_SIMPLE = findHandle(boolean.class,
        "isOnRenderThread");
    private static final MethodHandle RECORD_RENDER_CALL = findHandle(void.class,
        "recordRenderCall", Runnable.class);
    private static final MethodHandle QUEUE_RENDER_CALL = findHandle(void.class,
        "queueRenderCall", Runnable.class);

    private RenderSystemCompat() {}

    public static boolean isOnRenderThread() {
        Object result = invokeHandle(IS_ON_RENDER_THREAD, RenderSystem.class);
        if (result instanceof Boolean b) {
            return b;
        }
        result = invokeHandle(IS_ON_RENDER_THREAD_SIMPLE, RenderSystem.class);
        if (result instanceof Boolean b) {
            return b;
        }
        return false;
    }

    public static void recordRenderCall(Runnable task) {
        if (task == null) {
            return;
        }
        if (invokeHandle(RECORD_RENDER_CALL, RenderSystem.class, task) != null) {
            return;
        }
        if (invokeHandle(QUEUE_RENDER_CALL, RenderSystem.class, task) != null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            mc.execute(task);
        } else {
            task.run();
        }
    }

    private static MethodHandle findHandle(Class<?> returnType, String name, Class<?>... params) {
        try {
            return LOOKUP.findStatic(RenderSystem.class, name, MethodType.methodType(returnType, params));
        } catch (NoSuchMethodException | IllegalAccessException ignored) {
            return null;
        }
    }

    private static Object invokeHandle(MethodHandle handle, Object target, Object... args) {
        if (handle == null) {
            return null;
        }
        try {
            if (target == RenderSystem.class) {
                return handle.invokeWithArguments(args);
            }
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
