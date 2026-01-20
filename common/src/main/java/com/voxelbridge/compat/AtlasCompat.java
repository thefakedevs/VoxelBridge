package com.voxelbridge.compat;

import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * Cross-platform TextureAtlas utilities.
 * Uses Fabric Mixin accessor when available, falls back to NeoForge method or reflection.
 */
public final class AtlasCompat {

    private static final MethodHandles.Lookup LOOKUP = MethodHandles.publicLookup();
    
    // Try Fabric accessor class first
    private static final Class<?> FABRIC_ATLAS_ACCESS = findClass("com.voxelbridge.compat.FabricAtlasAccess");
    private static final MethodHandle FABRIC_GET_ALL_SPRITES = FABRIC_ATLAS_ACCESS != null 
        ? findStaticHandle(FABRIC_ATLAS_ACCESS, Collection.class, "getAllSprites", TextureAtlas.class)
        : null;
    
    // NeoForge method (getTextures)
    private static final MethodHandle GET_TEXTURES = findHandle(
        TextureAtlas.class, Map.class, "getTextures");

    private AtlasCompat() {}

    /**
     * Gets all sprites from a texture atlas.
     * Works on both NeoForge (via getTextures()) and Fabric (via Mixin accessor).
     */
    @SuppressWarnings("unchecked")
    public static Collection<TextureAtlasSprite> getAllSprites(TextureAtlas atlas) {
        if (atlas == null) {
            return Collections.emptyList();
        }
        
        // Try Fabric Mixin accessor first
        if (FABRIC_GET_ALL_SPRITES != null) {
            try {
                Object result = FABRIC_GET_ALL_SPRITES.invoke(atlas);
                if (result instanceof Collection<?> col) {
                    return (Collection<TextureAtlasSprite>) col;
                }
            } catch (Throwable ignored) {}
        }
        
        // Try NeoForge method (getTextures)
        if (GET_TEXTURES != null) {
            try {
                Object result = GET_TEXTURES.invoke(atlas);
                if (result instanceof Map<?, ?> map) {
                    return ((Map<ResourceLocation, TextureAtlasSprite>) map).values();
                }
            } catch (Throwable ignored) {}
        }
        
        return Collections.emptyList();
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
