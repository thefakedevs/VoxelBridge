package com.voxelbridge.compat;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Version-agnostic access to baked quad data.
 * Uses Fabric Mixin accessor when available, falls back to NeoForge methods.
 */
public final class QuadCompat {

    private static final MethodHandles.Lookup LOOKUP = MethodHandles.publicLookup();

    // Try Fabric accessor class first
    private static final Class<?> FABRIC_QUAD_ACCESS = findClass("com.voxelbridge.compat.FabricQuadAccess");
    private static final MethodHandle FABRIC_GET_SPRITE = FABRIC_QUAD_ACCESS != null 
        ? findStaticHandle(FABRIC_QUAD_ACCESS, TextureAtlasSprite.class, "getSprite", BakedQuad.class) : null;
    private static final MethodHandle FABRIC_GET_DIRECTION = FABRIC_QUAD_ACCESS != null 
        ? findStaticHandle(FABRIC_QUAD_ACCESS, Direction.class, "getDirection", BakedQuad.class) : null;
    private static final MethodHandle FABRIC_GET_VERTICES = FABRIC_QUAD_ACCESS != null 
        ? findStaticHandle(FABRIC_QUAD_ACCESS, int[].class, "getVertices", BakedQuad.class) : null;
    private static final MethodHandle FABRIC_GET_TINT = FABRIC_QUAD_ACCESS != null 
        ? findStaticHandle(FABRIC_QUAD_ACCESS, int.class, "getTintIndex", BakedQuad.class) : null;

    // NeoForge methods
    private static final MethodHandle SPRITE_HANDLE = findHandle(TextureAtlasSprite.class,
        "getSprite", "sprite");
    private static final MethodHandle DIRECTION_HANDLE = findHandle(Direction.class,
        "getDirection", "direction");
    private static final MethodHandle VERTICES_HANDLE = findHandle(int[].class,
        "getVertices", "vertices");
    private static final MethodHandle TINT_HANDLE = findHandle(int.class,
        "getTintIndex", "tintIndex");

    private QuadCompat() {}

    public static TextureAtlasSprite getSprite(BakedQuad quad) {
        if (quad == null) return null;
        
        // Try Fabric Mixin accessor first
        if (FABRIC_GET_SPRITE != null) {
            try {
                Object result = FABRIC_GET_SPRITE.invoke(quad);
                if (result instanceof TextureAtlasSprite s) return s;
            } catch (Throwable ignored) {}
        }
        
        // Try NeoForge method
        TextureAtlasSprite sprite = (TextureAtlasSprite) invokeHandle(SPRITE_HANDLE, quad);
        if (sprite != null) return sprite;
        
        return null;
    }

    public static Direction getDirection(BakedQuad quad) {
        if (quad == null) return null;
        
        // Try Fabric Mixin accessor first
        if (FABRIC_GET_DIRECTION != null) {
            try {
                Object result = FABRIC_GET_DIRECTION.invoke(quad);
                if (result instanceof Direction d) return d;
            } catch (Throwable ignored) {}
        }
        
        // Try NeoForge method
        Direction dir = (Direction) invokeHandle(DIRECTION_HANDLE, quad);
        if (dir != null) return dir;
        
        return null;
    }

    public static int[] getVertices(BakedQuad quad) {
        if (quad == null) return new int[0];
        
        // Try Fabric Mixin accessor first
        if (FABRIC_GET_VERTICES != null) {
            try {
                Object result = FABRIC_GET_VERTICES.invoke(quad);
                if (result instanceof int[] v) return v;
            } catch (Throwable ignored) {}
        }
        
        // Try NeoForge method
        int[] vertices = (int[]) invokeHandle(VERTICES_HANDLE, quad);
        return vertices != null ? vertices : new int[0];
    }

    public static int getTintIndex(BakedQuad quad) {
        if (quad == null) return -1;
        
        // Try Fabric Mixin accessor first
        if (FABRIC_GET_TINT != null) {
            try {
                Object result = FABRIC_GET_TINT.invoke(quad);
                if (result instanceof Integer i) return i;
            } catch (Throwable ignored) {}
        }
        
        // Try NeoForge method
        Object value = invokeHandle(TINT_HANDLE, quad);
        return value instanceof Integer i ? i : -1;
    }

    private static Class<?> findClass(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException ignored) {
            return null;
        }
    }

    private static MethodHandle findHandle(Class<?> returnType, String... names) {
        for (String name : names) {
            try {
                return LOOKUP.findVirtual(BakedQuad.class, name, MethodType.methodType(returnType));
            } catch (NoSuchMethodException | IllegalAccessException ignored) {}
        }
        return null;
    }

    private static MethodHandle findStaticHandle(Class<?> target, Class<?> returnType, String name, Class<?>... params) {
        try {
            return LOOKUP.findStatic(target, name, MethodType.methodType(returnType, params));
        } catch (NoSuchMethodException | IllegalAccessException ignored) {
            return null;
        }
    }

    private static Object invokeHandle(MethodHandle handle, Object target) {
        if (handle == null) return null;
        try {
            return handle.invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
