package com.voxelbridge.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Version-agnostic helpers for BlockState API drift.
 */
public final class BlockStateCompat {

    private static final MethodHandles.Lookup LOOKUP = MethodHandles.publicLookup();

    private static final MethodHandle OFFSET_LEVEL_HANDLE = findHandle(Vec3.class, "getOffset",
        Level.class, BlockPos.class);
    private static final MethodHandle OFFSET_POS_HANDLE = findHandle(Vec3.class, "getOffset",
        BlockPos.class);

    private static final MethodHandle SOLID_RENDER_LEVEL_HANDLE = findHandle(boolean.class, "isSolidRender",
        BlockAndTintGetter.class, BlockPos.class);
    private static final MethodHandle SOLID_RENDER_HANDLE = findHandle(boolean.class, "isSolidRender");

    private BlockStateCompat() {}

    public static Vec3 getOffset(BlockState state, Level level, BlockPos pos) {
        if (state == null || pos == null) {
            return Vec3.ZERO;
        }
        Vec3 result = (Vec3) invokeHandle(OFFSET_LEVEL_HANDLE, state, level, pos);
        if (result != null) {
            return result;
        }
        result = (Vec3) invokeHandle(OFFSET_POS_HANDLE, state, pos);
        return result != null ? result : Vec3.ZERO;
    }

    public static boolean isSolidRender(BlockState state, Level level, BlockPos pos) {
        if (state == null) {
            return false;
        }
        Object result = invokeHandle(SOLID_RENDER_LEVEL_HANDLE, state, level, pos);
        if (result instanceof Boolean b) {
            return b;
        }
        result = invokeHandle(SOLID_RENDER_HANDLE, state);
        if (result instanceof Boolean b) {
            return b;
        }
        return false;
    }

    private static MethodHandle findHandle(Class<?> returnType, String name, Class<?>... params) {
        try {
            return LOOKUP.findVirtual(BlockState.class, name, MethodType.methodType(returnType, params));
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
