package com.voxelbridge.compat;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Cross-platform sprite utilities for accessing internal data.
 * Uses Fabric Mixin accessor when available, falls back to NeoForge method.
 */
public final class SpriteCompat {

    private static final MethodHandles.Lookup LOOKUP = MethodHandles.publicLookup();
    
    // Try Fabric accessor class first
    private static final Class<?> FABRIC_SPRITE_ACCESS = findClass("com.voxelbridge.compat.FabricSpriteAccess");
    private static final MethodHandle FABRIC_GET_ORIGINAL_IMAGE = FABRIC_SPRITE_ACCESS != null 
        ? findStaticHandle(FABRIC_SPRITE_ACCESS, NativeImage.class, "getOriginalImage", TextureAtlasSprite.class)
        : null;
    
    // NeoForge method (getOriginalImage on SpriteContents)
    private static final MethodHandle GET_ORIGINAL_IMAGE = findHandle(
        SpriteContents.class, NativeImage.class, "getOriginalImage");

    private SpriteCompat() {}

    /**
     * Gets the original NativeImage from a sprite.
     * Works on both NeoForge (via getOriginalImage()) and Fabric (via Mixin accessor).
     */
    public static NativeImage getOriginalImage(TextureAtlasSprite sprite) {
        if (sprite == null) {
            return null;
        }
        SpriteContents contents = sprite.contents();
        if (contents == null) {
            return null;
        }
        
        // Try Fabric Mixin accessor first
        if (FABRIC_GET_ORIGINAL_IMAGE != null) {
            try {
                Object result = FABRIC_GET_ORIGINAL_IMAGE.invoke(sprite);
                if (result instanceof NativeImage img) {
                    return img;
                }
            } catch (Throwable ignored) {}
        }
        
        // Try NeoForge method
        if (GET_ORIGINAL_IMAGE != null) {
            try {
                Object result = GET_ORIGINAL_IMAGE.invoke(contents);
                if (result instanceof NativeImage img) {
                    return img;
                }
            } catch (Throwable ignored) {}
        }
        
        return null;
    }

    private static Class<?> findClass(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException ignored) {
            return null;
        }
    }

    private static MethodHandle findHandle(Class<?> target, Class<?> returnType, String name, Class<?>... params) {
        try {
            return LOOKUP.findVirtual(target, name, MethodType.methodType(returnType, params));
        } catch (NoSuchMethodException | IllegalAccessException ignored) {
            return null;
        }
    }

    private static MethodHandle findStaticHandle(Class<?> target, Class<?> returnType, String name, Class<?>... params) {
        try {
            return LOOKUP.findStatic(target, name, MethodType.methodType(returnType, params));
        } catch (NoSuchMethodException | IllegalAccessException ignored) {
            return null;
        }
    }
}
