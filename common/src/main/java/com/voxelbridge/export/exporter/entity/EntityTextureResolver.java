package com.voxelbridge.export.exporter.entity;

import com.voxelbridge.export.exporter.resolve.ResolvedTexture;
import com.voxelbridge.export.exporter.resolve.TextureResolver;
import com.voxelbridge.platform.client.ClientAccessHolder;
import com.voxelbridge.platform.render.RenderTypeTextureResolver;
import com.voxelbridge.util.debug.LogModule;
import com.voxelbridge.util.debug.VoxelBridgeLogger;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.decoration.Painting;
import net.minecraft.world.entity.decoration.PaintingVariant;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * Resolves textures for entity renderers with entity-specific overrides.
 */
public final class EntityTextureResolver implements TextureResolver<Entity> {

    public static final EntityTextureResolver INSTANCE = new EntityTextureResolver();

    private EntityTextureResolver() {}

    @Override
    public ResolvedTexture resolve(Entity entity, RenderType renderType) {
        // Try entity-specific resolvers first
        ResolvedTexture specific = resolveEntitySpecific(entity, renderType);
        if (specific != null) {
            return specific;
        }

        // Fall back to generic RenderType-based resolution
        ResourceLocation base = RenderTypeTextureResolver.INSTANCE.resolve(renderType);
        if (base == null) {
            return null;
        }
        // Some renderers produce paths with an extra ':' inside (e.g. "textures:models/...").
        if (base.getPath().contains(":")) {
            base = ResourceLocation.fromNamespaceAndPath(base.getNamespace(), base.getPath().replace(':', '/'));
        }
        return resolveTextureWithAtlasDetection(base);
    }

    private static ResolvedTexture resolveEntitySpecific(Entity entity, RenderType renderType) {
        // Handle Painting entities
        if (entity instanceof Painting painting) {
            return resolvePaintingTexture(painting);
        }
        // Handle ItemFrame entities (including GlowItemFrame)
        if (entity instanceof ItemFrame itemFrame) {
            return resolveItemFrameTexture(itemFrame, renderType);
        }
        return null;
    }

    private static ResolvedTexture resolvePaintingTexture(Painting painting) {
        try {
            // getVariant() returns Holder<PaintingVariant>, not Optional
            Holder<PaintingVariant> variantHolder = painting.getVariant();
            PaintingVariant variant = variantHolder.value();
            ResourceLocation texture = variant.assetId();

            ResolvedTexture atlasResolved = resolvePaintingAtlasSprite(texture);
            if (atlasResolved != null) {
                return atlasResolved;
            }

            VoxelBridgeLogger.debug(LogModule.ENTITY, String.format(
                "[EntityData] %s %s=%s",
                painting.getType(), "painting_variant", variant.assetId()));
            VoxelBridgeLogger.debug(LogModule.ENTITY, String.format(
                "[EntityData] %s %s=%s",
                painting.getType(), "painting_size", variant.width() + "x" + variant.height()));

            // Paintings use textures under textures/painting/<id>.png (variant assetId is usually "painting/<id>")
            if (!texture.getPath().startsWith("textures/")) {
                String path = texture.getPath();
                if (path.startsWith("painting/")) {
                    path = "textures/" + path + ".png";
                } else {
                    path = "textures/painting/" + path + ".png";
                }
                texture = ResourceLocation.fromNamespaceAndPath(texture.getNamespace(), path);
            }

            VoxelBridgeLogger.debug(LogModule.ENTITY, "[Painting] Resolved texture: " + texture);
            return new ResolvedTexture(texture, 0f, 1f, 0f, 1f, false, null, null);
        } catch (Exception e) {
            VoxelBridgeLogger.warn(LogModule.ENTITY, "[EntityTextureResolver] Failed to resolve painting texture: " + e.getMessage());
            return null;
        }
    }

    private static ResolvedTexture resolvePaintingAtlasSprite(ResourceLocation texture) {
        if (texture == null) {
            return null;
        }
        try {
            var paintingAtlas = ClientAccessHolder.get().getPaintingTextures();
            TextureAtlasSprite directSprite = tryFindPaintingSprite(paintingAtlas, texture);
            if (directSprite != null && !isMissingSprite(directSprite)) {
                ResourceLocation atlas = directSprite.atlasLocation();
                ResourceLocation spriteName = directSprite.contents() != null ? directSprite.contents().name() : texture;
                spriteName = normalizePaintingSpriteName(spriteName);
                VoxelBridgeLogger.debug(LogModule.ENTITY, "[Painting] Resolved sprite in painting atlas: " + atlas);
                return new ResolvedTexture(spriteName, directSprite.getU0(), directSprite.getU1(),
                    directSprite.getV0(), directSprite.getV1(), true, directSprite, atlas);
            }

            ResourceLocation atlas = resolvePaintingAtlasLocation(paintingAtlas);
            if (atlas != null) {
                var atlasGetter = ClientAccessHolder.get().getTextureAtlas(atlas);
                if (atlasGetter != null) {
                    var sprite = atlasGetter.apply(texture);
                    if (sprite != null && !isMissingSprite(sprite)) {
                        ResourceLocation spriteName = sprite.contents() != null ? sprite.contents().name() : texture;
                        spriteName = normalizePaintingSpriteName(spriteName);
                        VoxelBridgeLogger.debug(LogModule.ENTITY, "[Painting] Resolved sprite in painting atlas: " + atlas);
                        return new ResolvedTexture(spriteName, sprite.getU0(), sprite.getU1(),
                            sprite.getV0(), sprite.getV1(), true, sprite, atlas);
                    }
                }
            }
        } catch (Exception e) {
            VoxelBridgeLogger.debug(LogModule.ENTITY, "[Painting] Atlas sprite lookup failed: " + e.getMessage());
        }
        return null;
    }

    private static TextureAtlasSprite tryFindPaintingSprite(Object paintingAtlas, ResourceLocation texture) {
        if (paintingAtlas == null || texture == null) {
            return null;
        }
        Class<?> type = paintingAtlas.getClass();
        for (Method method : type.getMethods()) {
            if (method.getParameterCount() != 1) {
                continue;
            }
            if (!TextureAtlasSprite.class.isAssignableFrom(method.getReturnType())) {
                continue;
            }
            Class<?> param = method.getParameterTypes()[0];
            if (!param.isAssignableFrom(ResourceLocation.class)) {
                continue;
            }
            try {
                method.setAccessible(true);
                Object value = method.invoke(paintingAtlas, texture);
                if (value instanceof TextureAtlasSprite sprite) {
                    return sprite;
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }

        for (Field field : type.getDeclaredFields()) {
            if (!Map.class.isAssignableFrom(field.getType())) {
                continue;
            }
            try {
                field.setAccessible(true);
                Object value = field.get(paintingAtlas);
                if (value instanceof Map<?, ?> map) {
                    Object sprite = map.get(texture);
                    if (sprite instanceof TextureAtlasSprite atlasSprite) {
                        return atlasSprite;
                    }
                }
            } catch (ReflectiveOperationException ignored) {
            }
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

        // Fallback: vanilla painting atlas path used by recent versions.
        return ResourceLocation.fromNamespaceAndPath("minecraft", "textures/atlas/paintings.png");
    }

    private static ResourceLocation normalizePaintingSpriteName(ResourceLocation spriteName) {
        if (spriteName == null) {
            return null;
        }
        String path = spriteName.getPath();
        if (path.startsWith("textures/painting/") || path.startsWith("painting/")) {
            return spriteName;
        }
        return ResourceLocation.fromNamespaceAndPath(spriteName.getNamespace(), "painting/" + path);
    }

    private static ResolvedTexture resolveItemFrameTexture(ItemFrame itemFrame, RenderType renderType) {
        try {
            ItemStack item = itemFrame.getItem();
            boolean hasItem = item != null && !item.isEmpty();

            VoxelBridgeLogger.debug(LogModule.ENTITY, String.format(
                "[EntityData] %s %s=%s",
                itemFrame.getType(), "has_item", hasItem));
            if (hasItem) {
                VoxelBridgeLogger.debug(LogModule.ENTITY, String.format(
                    "[EntityData] %s %s=%s",
                    itemFrame.getType(), "item", item.getItem()));
            }
            VoxelBridgeLogger.debug(LogModule.ENTITY, String.format(
                "[EntityData] %s %s=%s",
                itemFrame.getType(), "direction", itemFrame.getDirection()));

            // Item frames use the block atlas; resolve to an atlas sprite via RenderType first.
            ResourceLocation base = RenderTypeTextureResolver.INSTANCE.resolve(renderType);
            if (base != null) {
                VoxelBridgeLogger.debug(LogModule.ENTITY, "[ItemFrame] Resolved texture from RenderType: " + base);
                return resolveTextureWithAtlasDetection(base);
            }

            // Fallback to entity texture if RenderType is not atlas-backed.
            boolean isGlowFrame = itemFrame instanceof net.minecraft.world.entity.decoration.GlowItemFrame;
            String framePath = isGlowFrame ?
                "textures/entity/glow_item_frame.png" :
                "textures/entity/item_frame.png";
            ResourceLocation frameTexture = ResourceLocation.fromNamespaceAndPath("minecraft", framePath);

            VoxelBridgeLogger.debug(LogModule.ENTITY, "[ItemFrame] Fallback frame texture: " + frameTexture);
            return new ResolvedTexture(frameTexture, 0f, 1f, 0f, 1f, false, null, null);
        } catch (Exception e) {
            VoxelBridgeLogger.warn(LogModule.ENTITY, "[EntityTextureResolver] Failed to resolve item frame texture: " + e.getMessage());
            return null;
        }
    }

    private static ResolvedTexture resolveTextureWithAtlasDetection(ResourceLocation texture) {
        // Handle known atlases first (chest/sign/bed, etc.) although entities rarely use them.
        ResourceLocation[] knownAtlases = {
            Sheets.CHEST_SHEET,
            Sheets.BED_SHEET,
            Sheets.SIGN_SHEET,
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/atlas/decorated_pot.png"),
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/atlas/shulker_boxes.png"),
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/atlas/banner_patterns.png"),
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/atlas/shield_patterns.png")
        };

        for (ResourceLocation atlas : knownAtlases) {
            try {
                var atlasGetter = ClientAccessHolder.get().getTextureAtlas(atlas);
                if (atlasGetter != null) {
                    var sprite = atlasGetter.apply(texture);
                    if (sprite != null && !isMissingSprite(sprite)) {
                        return new ResolvedTexture(texture, sprite.getU0(), sprite.getU1(),
                            sprite.getV0(), sprite.getV1(), true, sprite, atlas);
                    }
                }
            } catch (Exception ignored) {
            }
        }

        // Not found in atlases; if path hints an atlas, treat as atlas bounds, otherwise standalone
        if (texture != null && texture.getPath().startsWith("textures/atlas/")) {
            return new ResolvedTexture(texture, 0f, 1f, 0f, 1f, true, null, texture);
        }
        return new ResolvedTexture(texture, 0f, 1f, 0f, 1f, false, null, null);
    }

    private static boolean isMissingSprite(net.minecraft.client.renderer.texture.TextureAtlasSprite sprite) {
        return sprite.contents().name().toString().contains("missingno");
    }
}
