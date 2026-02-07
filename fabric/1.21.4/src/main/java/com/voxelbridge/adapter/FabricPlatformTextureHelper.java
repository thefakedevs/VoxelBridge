package com.voxelbridge.adapter;

import com.mojang.blaze3d.platform.NativeImage;
import com.voxelbridge.compat.FabricAtlasAccess;
import com.voxelbridge.compat.FabricSpriteAccess;
import com.voxelbridge.compat.FabricTextureAccess;
import com.voxelbridge.export.exporter.resolve.ResolvedTexture;
import com.voxelbridge.export.texture.MapTextureUtil;
import com.voxelbridge.util.debug.LogModule;
import com.voxelbridge.util.debug.VoxelBridgeLogger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
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
import java.util.Optional;

/**
 * Fabric implementation of PlatformTextureHelper.
 */
public class FabricPlatformTextureHelper implements PlatformTextureHelper {

    @Override
    public int getPixelRgba(NativeImage img, int x, int y) {
        if (img == null) {
            return 0;
        }
        int abgr = img.getPixel(x, y);
        return swapRedBlue(abgr);
    }

    @Override
    public NativeImage getOriginalImage(TextureAtlasSprite sprite) {
        if (sprite == null) {
            return null;
        }
        return FabricSpriteAccess.getOriginalImage(sprite);
    }

    @Override
    public Collection<TextureAtlasSprite> getAllSprites(TextureAtlas atlas) {
        if (atlas == null) {
            return Collections.emptyList();
        }
        return FabricAtlasAccess.getAllSprites(atlas);
    }

    @Override
    public Optional<NativeImage> readTexture(ResourceLocation location) {
        if (location == null) {
            return Optional.empty();
        }
        ResourceLocation normalized = MapTextureUtil.normalizeDynamicMapLocation(location);
        if (normalized != null) {
            location = normalized;
            if (VoxelBridgeLogger.isDebugEnabled(LogModule.DYNAMIC_MAP)) {
                VoxelBridgeLogger.debug(LogModule.DYNAMIC_MAP, "[FabricTextureHelper/1.21.4] Normalized map location: " + location);
            }
        }

        // 1) Dynamic / HTTP textures (render thread)
        if (com.mojang.blaze3d.systems.RenderSystem.isOnRenderThread()) {
            preheatMapTexture(location);
            var tm = Minecraft.getInstance().getTextureManager();
            var texture = tm.getTexture(location);

            NativeImage pixels = FabricTextureAccess.getDynamicTexturePixels(texture);
            if (pixels != null) {
                if (VoxelBridgeLogger.isDebugEnabled(LogModule.DYNAMIC_MAP)) {
                    VoxelBridgeLogger.debug(LogModule.DYNAMIC_MAP, "[FabricTextureHelper/1.21.4] DynamicTexture pixels loaded: " + location);
                }
                return Optional.of(copyNativeImage(pixels));
            }

            File file = FabricTextureAccess.getHttpTextureFile(texture);
            if (file != null && file.exists()) {
                try {
                    return Optional.of(NativeImage.read(new FileInputStream(file)));
                } catch (Exception ignored) {
                }
            }
        }

        // 2) Resource manager fallback
        try {
            var resource = Minecraft.getInstance().getResourceManager().getResource(location);
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

    private static int swapRedBlue(int abgr) {
        int a = (abgr >>> 24) & 0xFF;
        int b = (abgr >>> 16) & 0xFF;
        int g = (abgr >>> 8) & 0xFF;
        int r = abgr & 0xFF;
        return (a << 24) | (r << 16) | (g << 8) | b;
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

            TextureAtlasSprite sprite = Minecraft.getInstance().getPaintingTextures().get(variant);
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

            // Fallback to file path
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
        try {
            ResourceLocation woodLoc = ResourceLocation.withDefaultNamespace("block/birch_planks");
            TextureAtlasSprite sprite = Minecraft.getInstance()
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
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        var mapRenderer = mc.gameRenderer.getMapRenderer();
        if (mapRenderer == null) {
            return;
        }
        try {
            MapItemSavedData data = MapItem.getSavedData(id, mc.level);
            if (data != null) {
                if (VoxelBridgeLogger.isDebugEnabled(LogModule.DYNAMIC_MAP)) {
                    VoxelBridgeLogger.debug(LogModule.DYNAMIC_MAP, "[FabricTextureHelper/1.21.4] MapRenderer.update: " + id);
                }
                mapRenderer.update(id, data);
            }
        } catch (Exception ignored) {
        }
    }
}
