package com.voxelbridge.export.exporter.entity;

import com.voxelbridge.export.exporter.resolve.ResolvedTexture;
import com.voxelbridge.export.exporter.resolve.TextureResolver;
import com.voxelbridge.platform.client.ClientAccessHolder;
import com.voxelbridge.util.debug.LogModule;
import com.voxelbridge.util.debug.VoxelBridgeLogger;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.Painting;

import java.lang.reflect.Method;

/**
 * 1.21.4-specific entity texture resolver override for paintings.
 */
public final class EntityTextureResolverCompat implements TextureResolver<Entity> {

    public static final EntityTextureResolverCompat INSTANCE = new EntityTextureResolverCompat();

    private EntityTextureResolverCompat() {}

    @Override
    public ResolvedTexture resolve(Entity entity, RenderType renderType) {
        if (entity instanceof Painting painting) {
            ResolvedTexture atlasResolved = resolvePaintingAtlas(painting);
            if (atlasResolved != null) {
                return atlasResolved;
            }
        }
        return EntityTextureResolver.INSTANCE.resolve(entity, renderType);
    }

    private static ResolvedTexture resolvePaintingAtlas(Painting painting) {
        try {
            Object paintingAtlas = ClientAccessHolder.get().getPaintingTextures();
            ResourceLocation atlas = resolvePaintingAtlasLocation(paintingAtlas);
            if (atlas != null) {
                VoxelBridgeLogger.debug(LogModule.ENTITY, "[Painting] Using atlas locator for painting atlas: " + atlas);
                return new ResolvedTexture(atlas, 0f, 1f, 0f, 1f, true, null, atlas);
            }
        } catch (Exception e) {
            VoxelBridgeLogger.debug(LogModule.ENTITY, "[Painting] Atlas lookup failed: " + e.getMessage());
        }
        return null;
    }

    private static ResourceLocation resolvePaintingAtlasLocation(Object paintingAtlas) {
        if (paintingAtlas == null) {
            return null;
        }
        try {
            Method backSpriteMethod = paintingAtlas.getClass().getMethod("getBackSprite");
            Object value = backSpriteMethod.invoke(paintingAtlas);
            if (value instanceof TextureAtlasSprite sprite) {
                return sprite.atlasLocation();
            }
        } catch (ReflectiveOperationException ignored) {
        }

        for (Method method : paintingAtlas.getClass().getMethods()) {
            if (method.getParameterCount() != 0) {
                continue;
            }
            if (!ResourceLocation.class.isAssignableFrom(method.getReturnType())) {
                continue;
            }
            String name = method.getName().toLowerCase(java.util.Locale.ROOT);
            if (!name.contains("atlas")) {
                continue;
            }
            try {
                Object value = method.invoke(paintingAtlas);
                if (value instanceof ResourceLocation loc) {
                    return loc;
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }

        return ResourceLocation.fromNamespaceAndPath("minecraft", "textures/atlas/paintings.png");
    }
}
