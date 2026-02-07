package com.voxelbridge.adapter;

import com.mojang.blaze3d.platform.NativeImage;
import com.voxelbridge.export.exporter.resolve.ResolvedTexture;
import com.voxelbridge.export.texture.MapTextureUtil;
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
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.io.File;
import java.io.FileInputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

/**
 * NeoForge implementation of PlatformTextureHelper.
 */
public class NeoForgePlatformTextureHelper implements PlatformTextureHelper {

    @Override
    public int getPixelRgba(NativeImage img, int x, int y) {
        if (img == null)
            return 0;
        // 1.21.4: getPixel returns ARGB, convert to ABGR expected by common pipeline.
        int argb = img.getPixel(x, y);
        return (argb & 0xFF00FF00) | ((argb & 0x00FF0000) >> 16) | ((argb & 0x000000FF) << 16);
    }

    @Override
    public NativeImage getOriginalImage(TextureAtlasSprite sprite) {
        if (sprite == null)
            return null;
        SpriteContents contents = sprite.contents();
        if (contents == null)
            return null;
        return ((com.voxelbridge.mixin.SpriteContentsAccessor) (Object) contents).voxelbridge$getOriginalImage();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Collection<TextureAtlasSprite> getAllSprites(TextureAtlas atlas) {
        if (atlas == null)
            return Collections.emptyList();
        Map<ResourceLocation, TextureAtlasSprite> map =
                ((com.voxelbridge.mixin.TextureAtlasAccessor) (Object) atlas).voxelbridge$getTextures();
        return map != null ? map.values() : Collections.emptyList();
    }

    @Override
    public Optional<NativeImage> readTexture(ResourceLocation location) {
        if (location == null)
            return Optional.empty();
        ResourceLocation normalized = MapTextureUtil.normalizeDynamicMapLocation(location);
        if (normalized != null) {
            location = normalized;
            if (VoxelBridgeLogger.isDebugEnabled(LogModule.DYNAMIC_MAP)) {
                VoxelBridgeLogger.debug(LogModule.DYNAMIC_MAP, "[NeoForgeTextureHelper/1.21.4] Normalized map location: " + location);
            }
        }

        // 1. Try Memory (DynamicTexture) - Main Thread Operation
        if (com.mojang.blaze3d.systems.RenderSystem.isOnRenderThread()) {
            preheatMapTexture(location);
            net.minecraft.client.renderer.texture.TextureManager tm = net.minecraft.client.Minecraft.getInstance()
                    .getTextureManager();
            net.minecraft.client.renderer.texture.AbstractTexture texture = tm.getTexture(location);

            if (texture instanceof net.minecraft.client.renderer.texture.DynamicTexture dt) {
                NativeImage pixels = dt.getPixels();
                if (pixels != null) {
                    if (VoxelBridgeLogger.isDebugEnabled(LogModule.DYNAMIC_MAP)) {
                        VoxelBridgeLogger.debug(LogModule.DYNAMIC_MAP, "[NeoForgeTextureHelper/1.21.4] DynamicTexture pixels loaded: " + location);
                    }
                    return Optional.of(copyNativeImage(pixels));
                }
            }

            // Note: HttpTexture is not available in 1.21.4, so we skip that check
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

            // Use native path: Get sprite directly from PaintingTextureManager.
            TextureAtlasSprite sprite = net.minecraft.client.Minecraft.getInstance()
                    .getPaintingTextures()
                    .get(variant);

            if (sprite != null) {
                ResourceLocation spriteName = sprite.contents() != null ? sprite.contents().name() : variant.assetId();
                spriteName = normalizePaintingSpriteName(spriteName);
                return new ResolvedTexture(
                        spriteName,
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
                ResourceLocation spriteName = sprite.contents() != null ? sprite.contents().name() : woodLoc;
                return new ResolvedTexture(
                        spriteName,
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

    private static void preheatMapTexture(ResourceLocation location) {
        int mapId = MapTextureUtil.parseMapId(location);
        if (mapId < 0) {
            return;
        }
        MapId id = new MapId(mapId);
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        var mapTextureManager = mc.getMapTextureManager();
        if (mapTextureManager == null) {
            return;
        }
        try {
            MapItemSavedData data = MapItem.getSavedData(id, mc.level);
            if (data != null) {
                if (VoxelBridgeLogger.isDebugEnabled(LogModule.DYNAMIC_MAP)) {
                    VoxelBridgeLogger.debug(LogModule.DYNAMIC_MAP, "[NeoForgeTextureHelper/1.21.4] MapTextureManager.update: " + id);
                }
                mapTextureManager.update(id, data);
            }
        } catch (Exception ignored) {
        }
    }

    // HttpTexture file access is handled via mixin accessor.
}
