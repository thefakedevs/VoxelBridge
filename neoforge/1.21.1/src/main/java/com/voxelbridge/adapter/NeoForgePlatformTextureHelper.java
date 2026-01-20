package com.voxelbridge.adapter;

import com.mojang.blaze3d.platform.NativeImage;
import com.voxelbridge.export.exporter.resolve.ResolvedTexture;
import com.voxelbridge.util.debug.LogModule;
import com.voxelbridge.util.debug.VoxelBridgeLogger;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.decoration.Painting;
import net.minecraft.world.entity.decoration.PaintingVariant;

import java.io.File;
import java.io.FileInputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

/**
 * NeoForge implementation of PlatformTextureHelper.
 */
public class NeoForgePlatformTextureHelper implements PlatformTextureHelper {

    private static final MethodHandles.Lookup LOOKUP = MethodHandles.publicLookup();

    // Method handles reused from previously removed helper logic
    private static final MethodHandle GET_ORIGINAL_IMAGE = findHandle(
            SpriteContents.class, NativeImage.class, "getOriginalImage");
    private static final MethodHandle GET_TEXTURES = findHandle(
            TextureAtlas.class, Map.class, "getTextures");

    @Override
    public int getPixelRgba(NativeImage img, int x, int y) {
        if (img == null)
            return 0;
        return img.getPixelRGBA(x, y);
    }

    @Override
    public NativeImage getOriginalImage(TextureAtlasSprite sprite) {
        if (sprite == null)
            return null;
        SpriteContents contents = sprite.contents();
        if (contents == null)
            return null;

        if (GET_ORIGINAL_IMAGE != null) {
            try {
                return (NativeImage) GET_ORIGINAL_IMAGE.invoke(contents);
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Collection<TextureAtlasSprite> getAllSprites(TextureAtlas atlas) {
        if (atlas == null)
            return Collections.emptyList();

        if (GET_TEXTURES != null) {
            try {
                Object result = GET_TEXTURES.invoke(atlas);
                if (result instanceof Map<?, ?> map) {
                    return ((Map<ResourceLocation, TextureAtlasSprite>) map).values();
                }
            } catch (Throwable ignored) {
            }
        }
        return Collections.emptyList();
    }

    @Override
    public Optional<NativeImage> readTexture(ResourceLocation location) {
        if (location == null)
            return Optional.empty();

        // 1. Try Memory (DynamicTexture / HttpTexture) - Main Thread Operation
        // preferred
        if (com.mojang.blaze3d.systems.RenderSystem.isOnRenderThread()) {
            net.minecraft.client.renderer.texture.TextureManager tm = net.minecraft.client.Minecraft.getInstance()
                    .getTextureManager();
            net.minecraft.client.renderer.texture.AbstractTexture texture = tm.getTexture(location);

            if (texture instanceof net.minecraft.client.renderer.texture.DynamicTexture dt) {
                NativeImage pixels = dt.getPixels();
                if (pixels != null) {
                    return Optional.of(copyNativeImage(pixels));
                }
            }

            if (texture instanceof net.minecraft.client.renderer.texture.HttpTexture) {
                File file = findFileField(texture);
                if (file != null && file.exists()) {
                    try {
                        return Optional.of(NativeImage.read(new FileInputStream(file)));
                    } catch (Exception ignored) {
                    }
                }
            }
        }

        // 2. Try Resource Manager (Static files)
        try {
            var resource = net.minecraft.client.Minecraft.getInstance().getResourceManager().getResource(location);
            if (resource.isPresent()) {
                try (var is = resource.get().open()) {
                    return Optional.of(NativeImage.read(is));
                }
            }
        } catch (Exception ignored) {
        }

        return Optional.empty();
    }

    @Override
    public void copyNativeImage(NativeImage src, NativeImage dst) {
        if (src != null && dst != null) {
            dst.copyFrom(src);
        }
    }

    private NativeImage copyNativeImage(NativeImage src) {
        NativeImage dst = new NativeImage(src.format(), src.getWidth(), src.getHeight(), false);
        dst.copyFrom(src);
        return dst;
    }

    @Override
    public ResolvedTexture resolveEntityTexture(Entity entity, RenderType type) {
        if (entity instanceof Painting painting) {
            return resolvePainting(painting);
        }
        if (entity instanceof ItemFrame frame) {
            return resolveItemFrame(frame);
        }
        return null;
    }

    private ResolvedTexture resolvePainting(Painting painting) {
        try {
            Holder<PaintingVariant> variantHolder = painting.getVariant();
            PaintingVariant variant = variantHolder.value();

            // Use optimal native path: Get sprite directly from PaintingTextureManager
            // The class PaintingTextureManager is package-private or hidden, so we use
            // reflection/inference
            Object manager = net.minecraft.client.Minecraft.getInstance().getPaintingTextures();
            TextureAtlasSprite sprite = null;
            try {
                // Try reflection to find 'get' method
                java.lang.reflect.Method getMethod = manager.getClass().getMethod("get", PaintingVariant.class);
                sprite = (TextureAtlasSprite) getMethod.invoke(manager, variant);
            } catch (Exception e) {
                // If reflection fails, we fall back to file path logic below
            }

            if (sprite != null) {
                return new ResolvedTexture(
                        sprite.atlasLocation(),
                        sprite.getU0(), sprite.getU1(), sprite.getV0(), sprite.getV1(),
                        true,
                        sprite,
                        sprite.atlasLocation());
            }

            // Fallback to file path if sprite missing (unlikely)
            ResourceLocation assetId = variant.assetId();
            String path = assetId.getPath();
            if (!path.startsWith("textures/")) {
                if (path.startsWith("painting/")) {
                    path = "textures/" + path;
                } else {
                    path = "textures/painting/" + path;
                }
            }
            if (!path.endsWith(".png")) {
                path = path + ".png";
            }
            ResourceLocation textureLoc = ResourceLocation.fromNamespaceAndPath(assetId.getNamespace(), path);
            return new ResolvedTexture(textureLoc, 0f, 1f, 0f, 1f, false, null, null);

        } catch (Exception e) {
            VoxelBridgeLogger.warn(LogModule.ENTITY, "Failed to resolve painting texture via platform helper: " + e);
            return null;
        }
    }

    private ResolvedTexture resolveItemFrame(ItemFrame frame) {
        // Item Frames use block textures (birch planks) for the backing.
        // We resolve this from the block atlas to ensure it looks correct.
        try {
            ResourceLocation woodLoc = ResourceLocation.withDefaultNamespace("block/birch_planks");
            TextureAtlasSprite sprite = net.minecraft.client.Minecraft.getInstance()
                    .getModelManager()
                    .getAtlas(TextureAtlas.LOCATION_BLOCKS)
                    .getSprite(woodLoc);

            if (sprite != null) {
                return new ResolvedTexture(
                        sprite.atlasLocation(),
                        sprite.getU0(), sprite.getU1(), sprite.getV0(), sprite.getV1(),
                        true,
                        sprite,
                        sprite.atlasLocation());
            }
        } catch (Exception ignored) {
        }

        // Fallback to the entity texture if block lookup fails
        boolean isGlow = frame instanceof net.minecraft.world.entity.decoration.GlowItemFrame;
        String path = isGlow ? "textures/entity/glow_item_frame.png" : "textures/entity/item_frame.png";
        ResourceLocation loc = ResourceLocation.fromNamespaceAndPath("minecraft", path);
        return new ResolvedTexture(loc, 0f, 1f, 0f, 1f, false, null, null);
    }

    private static MethodHandle findHandle(Class<?> target, Class<?> returnType, String name, Class<?>... params) {
        try {
            return LOOKUP.findVirtual(target, name, MethodType.methodType(returnType, params));
        } catch (NoSuchMethodException | IllegalAccessException ignored) {
            return null;
        }
    }

    private java.io.File findFileField(Object target) {
        Class<?> cls = target.getClass();
        while (cls != null && cls != Object.class) {
            for (java.lang.reflect.Field f : cls.getDeclaredFields()) {
                if (java.io.File.class.isAssignableFrom(f.getType())) {
                    try {
                        f.setAccessible(true);
                        return (java.io.File) f.get(target);
                    } catch (Exception ignored) {
                    }
                }
            }
            cls = cls.getSuperclass();
        }
        return null;
    }
}
