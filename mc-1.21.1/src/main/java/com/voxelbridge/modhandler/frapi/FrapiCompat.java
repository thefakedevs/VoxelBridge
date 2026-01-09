package com.voxelbridge.modhandler.frapi;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

/**
 * Reflection-only FRAPI bridge to keep block export working when FRAPI is not installed.
 */
public final class FrapiCompat {
    private static final String FABRIC_BAKED_MODEL = "net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel";
    private static final String SPRITE_FINDER = "net.fabricmc.fabric.api.renderer.v1.model.SpriteFinder";
    private static final String RENDERER_ACCESS = "net.fabricmc.fabric.api.renderer.v1.RendererAccess";
    private static final String HELPER = "com.voxelbridge.modhandler.frapi.FabricApiHelper";

    private static final boolean AVAILABLE = isClassPresent(FABRIC_BAKED_MODEL)
        && isClassPresent(SPRITE_FINDER)
        && isClassPresent(RENDERER_ACCESS);

    private FrapiCompat() {}

    public static boolean isAvailable() {
        return AVAILABLE;
    }

    public static Object getSpriteFinder(TextureAtlas atlas) {
        if (!AVAILABLE || atlas == null) {
            return null;
        }
        try {
            Class<?> spriteFinderClass = Class.forName(SPRITE_FINDER, false, FrapiCompat.class.getClassLoader());
            Method getMethod = spriteFinderClass.getMethod("get", TextureAtlas.class);
            return getMethod.invoke(null, atlas);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static List<BakedQuad> extractQuads(
        BakedModel model,
        BlockAndTintGetter level,
        BlockState state,
        BlockPos pos,
        RandomSource rand,
        Object spriteFinder
    ) {
        if (!AVAILABLE || model == null || spriteFinder == null) {
            return Collections.emptyList();
        }
        try {
            Class<?> fabricBakedModelClass = Class.forName(FABRIC_BAKED_MODEL, false, FrapiCompat.class.getClassLoader());
            if (!fabricBakedModelClass.isInstance(model)) {
                return Collections.emptyList();
            }

            Method isVanillaAdapter = fabricBakedModelClass.getMethod("isVanillaAdapter");
            boolean vanillaAdapter = Boolean.TRUE.equals(isVanillaAdapter.invoke(model));
            if (vanillaAdapter) {
                return Collections.emptyList();
            }

            Class<?> helperClass = Class.forName(HELPER, false, FrapiCompat.class.getClassLoader());
            Class<?> spriteFinderClass = Class.forName(SPRITE_FINDER, false, FrapiCompat.class.getClassLoader());
            Method extract = helperClass.getMethod(
                "extractQuads",
                fabricBakedModelClass,
                BlockAndTintGetter.class,
                BlockState.class,
                BlockPos.class,
                RandomSource.class,
                spriteFinderClass
            );

            @SuppressWarnings("unchecked")
            List<BakedQuad> quads = (List<BakedQuad>) extract.invoke(
                null, model, level, state, pos, rand, spriteFinder
            );
            return quads != null ? quads : Collections.emptyList();
        } catch (Throwable ignored) {
            return Collections.emptyList();
        }
    }

    private static boolean isClassPresent(String name) {
        try {
            Class.forName(name, false, FrapiCompat.class.getClassLoader());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
