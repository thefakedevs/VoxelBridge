package com.voxelbridge.compat;

import net.minecraft.client.gui.GuiGraphics;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Version-agnostic helpers for GuiGraphics pose access.
 */
public final class GuiPoseCompat {

    private static final Map<Class<?>, PoseOps> CACHE = new ConcurrentHashMap<>();

    private GuiPoseCompat() {}

    public static void push(GuiGraphics gfx) {
        Object pose = pose(gfx);
        if (pose == null) {
            return;
        }
        PoseOps ops = opsFor(pose.getClass());
        ops.invokePush(pose);
    }

    public static void pop(GuiGraphics gfx) {
        Object pose = pose(gfx);
        if (pose == null) {
            return;
        }
        PoseOps ops = opsFor(pose.getClass());
        ops.invokePop(pose);
    }

    public static void translate(GuiGraphics gfx, float x, float y, float z) {
        Object pose = pose(gfx);
        if (pose == null) {
            return;
        }
        PoseOps ops = opsFor(pose.getClass());
        ops.invokeTranslate(pose, x, y, z);
    }

    private static Object pose(GuiGraphics gfx) {
        return gfx != null ? gfx.pose() : null;
    }

    private static PoseOps opsFor(Class<?> type) {
        return CACHE.computeIfAbsent(type, PoseOps::new);
    }

    private static final class PoseOps {
        private static final MethodHandles.Lookup LOOKUP = MethodHandles.publicLookup();

        private final MethodHandle push;
        private final MethodHandle pop;
        private final MethodHandle translate3;
        private final MethodHandle translate2;

        PoseOps(Class<?> type) {
            this.push = findHandleAny(type, void.class, "pushPose", "push", "pushMatrix");
            this.pop = findHandleAny(type, void.class, "popPose", "pop", "popMatrix");
            this.translate3 = findHandle(type, void.class, "translate", float.class, float.class, float.class);
            this.translate2 = findHandle(type, void.class, "translate", float.class, float.class);
        }

        void invokePush(Object pose) {
            invoke(push, pose);
        }

        void invokePop(Object pose) {
            invoke(pop, pose);
        }

        void invokeTranslate(Object pose, float x, float y, float z) {
            if (translate3 != null) {
                invoke(translate3, pose, x, y, z);
                return;
            }
            if (translate2 != null) {
                invoke(translate2, pose, x, y);
            }
        }

        private static MethodHandle findHandle(Class<?> type, Class<?> returnType, String name, Class<?>... params) {
            try {
                return LOOKUP.findVirtual(type, name, MethodType.methodType(returnType, params));
            } catch (NoSuchMethodException | IllegalAccessException ignored) {
                return null;
            }
        }

        private static MethodHandle findHandleAny(Class<?> type, Class<?> returnType, String... names) {
            for (String name : names) {
                MethodHandle handle = findHandle(type, returnType, name);
                if (handle != null) {
                    return handle;
                }
            }
            return null;
        }

        private static void invoke(MethodHandle handle, Object target, Object... args) {
            if (handle == null) {
                return;
            }
            try {
                Object[] callArgs = new Object[args.length + 1];
                callArgs[0] = target;
                System.arraycopy(args, 0, callArgs, 1, args.length);
                handle.invokeWithArguments(callArgs);
            } catch (Throwable ignored) {
                // Ignore pose failures; HUD rendering will still proceed.
            }
        }
    }
}
