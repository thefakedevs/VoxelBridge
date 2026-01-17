package com.voxelbridge.compat;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;

/**
 * Version-agnostic access to baked quad data.
 */
public final class QuadCompat {

    private static final MethodHandles.Lookup LOOKUP = MethodHandles.publicLookup();

    private static final MethodHandle SPRITE_HANDLE = findHandle(TextureAtlasSprite.class,
        "getSprite", "sprite", "texture");
    private static final MethodHandle DIRECTION_HANDLE = findHandle(Direction.class,
        "getDirection", "direction", "face", "cullFace");
    private static final MethodHandle VERTICES_HANDLE = findHandle(int[].class,
        "getVertices", "vertices", "vertexData");
    private static final MethodHandle TINT_HANDLE = findHandle(int.class,
        "getTintIndex", "tintIndex", "colorIndex");

    private static final Field SPRITE_FIELD = findField(TextureAtlasSprite.class, "sprite", "texture");
    private static final Field DIRECTION_FIELD = findField(Direction.class, "direction", "face", "cullFace");
    private static final Field VERTICES_FIELD = findField(int[].class, "vertices", "vertexData");
    private static final Field TINT_FIELD = findField(int.class, "tintIndex", "colorIndex");

    private QuadCompat() {}

    public static TextureAtlasSprite getSprite(BakedQuad quad) {
        if (quad == null) {
            return null;
        }
        TextureAtlasSprite sprite = (TextureAtlasSprite) invokeHandle(SPRITE_HANDLE, quad);
        if (sprite != null) {
            return sprite;
        }
        return (TextureAtlasSprite) readField(SPRITE_FIELD, quad);
    }

    public static Direction getDirection(BakedQuad quad) {
        if (quad == null) {
            return null;
        }
        Direction dir = (Direction) invokeHandle(DIRECTION_HANDLE, quad);
        if (dir != null) {
            return dir;
        }
        return (Direction) readField(DIRECTION_FIELD, quad);
    }

    public static int[] getVertices(BakedQuad quad) {
        if (quad == null) {
            return new int[0];
        }
        int[] vertices = (int[]) invokeHandle(VERTICES_HANDLE, quad);
        if (vertices != null) {
            return vertices;
        }
        vertices = (int[]) readField(VERTICES_FIELD, quad);
        return vertices != null ? vertices : new int[0];
    }

    public static int getTintIndex(BakedQuad quad) {
        if (quad == null) {
            return -1;
        }
        Object value = invokeHandle(TINT_HANDLE, quad);
        if (value instanceof Integer i) {
            return i;
        }
        Object fieldValue = readField(TINT_FIELD, quad);
        return fieldValue instanceof Integer i ? i : -1;
    }

    private static MethodHandle findHandle(Class<?> returnType, String... names) {
        for (String name : names) {
            try {
                return LOOKUP.findVirtual(BakedQuad.class, name, MethodType.methodType(returnType));
            } catch (NoSuchMethodException | IllegalAccessException ignored) {
                // Try next name.
            }
        }
        return null;
    }

    private static Field findField(Class<?> type, String... names) {
        for (String name : names) {
            try {
                Field field = BakedQuad.class.getDeclaredField(name);
                if (!type.isAssignableFrom(field.getType())) {
                    continue;
                }
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                // Try next name.
            }
        }
        return null;
    }

    private static Object invokeHandle(MethodHandle handle, Object target) {
        if (handle == null) {
            return null;
        }
        try {
            return handle.invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object readField(Field field, Object target) {
        if (field == null) {
            return null;
        }
        try {
            return field.get(target);
        } catch (IllegalAccessException ignored) {
            return null;
        }
    }
}
