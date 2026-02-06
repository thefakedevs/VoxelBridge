package com.voxelbridge.adapter;

import com.mojang.blaze3d.platform.NativeImage;
import com.voxelbridge.compat.FabricTextureAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.MapRenderer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.io.File;
import java.io.FileInputStream;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fabric 1.20.1 dynamic texture cache for HttpTexture/DynamicTexture.
 * This avoids blocking worker threads by allowing async preheats on the render thread.
 */
public final class DynamicTextureCache {

    private static final ConcurrentHashMap<ResourceLocation, NativeImage> CACHE = new ConcurrentHashMap<>();
    private static final Set<ResourceLocation> IN_FLIGHT = ConcurrentHashMap.newKeySet();

    private DynamicTextureCache() {}

    public static void clear() {
        for (NativeImage img : CACHE.values()) {
            try {
                img.close();
            } catch (Exception ignored) {
            }
        }
        CACHE.clear();
        IN_FLIGHT.clear();
    }

    public static NativeImage get(ResourceLocation location) {
        return location == null ? null : CACHE.get(location);
    }

    /**
     * Queue a non-blocking preheat on the render thread.
     */
    public static void preheat(ResourceLocation location) {
        if (location == null) {
            return;
        }
        if (!IN_FLIGHT.add(location)) {
            return;
        }
        Minecraft.getInstance().execute(() -> {
            try {
                loadOnRenderThread(location);
            } finally {
                IN_FLIGHT.remove(location);
            }
        });
    }

    /**
     * Load a dynamic texture on the render thread and cache it.
     */
    public static Optional<NativeImage> loadOnRenderThread(ResourceLocation location) {
        if (location == null) {
            return Optional.empty();
        }
        NativeImage cached = CACHE.get(location);
        if (cached != null) {
            return Optional.of(cached);
        }

        Optional<NativeImage> mapImage = loadMapTexture(location);
        if (mapImage.isPresent()) {
            return mapImage;
        }

        AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(location);
        NativeImage pixels = FabricTextureAccess.getDynamicTexturePixels(texture);
        if (pixels != null) {
            NativeImage copy = copyNativeImage(pixels);
            CACHE.put(location, copy);
            return Optional.of(copy);
        }

        File file = FabricTextureAccess.getHttpTextureFile(texture);
        if (file != null && file.exists()) {
            try {
                NativeImage img = NativeImage.read(new FileInputStream(file));
                CACHE.put(location, img);
                return Optional.of(img);
            } catch (Exception ignored) {
            }
        }

        return Optional.empty();
    }

    private static Optional<NativeImage> loadMapTexture(ResourceLocation location) {
        int mapId = parseMapId(location);
        if (mapId < 0) {
            return Optional.empty();
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return Optional.empty();
        }

        MapRenderer mapRenderer = mc.gameRenderer.getMapRenderer();
        if (mapRenderer == null) {
            return Optional.empty();
        }

        try {
            MapItemSavedData data = MapItem.getSavedData(mapId, mc.level);
            if (data != null) {
                mapRenderer.update(mapId, data);
            }
        } catch (Exception ignored) {
        }

        try {
            it.unimi.dsi.fastutil.ints.Int2ObjectMap<?> maps =
                ((com.voxelbridge.mixin.MapRendererAccessor) (Object) mapRenderer).voxelbridge$getMaps();
            if (maps == null) {
                return Optional.empty();
            }
            Object instance = maps.get(mapId);
            if (instance == null) {
                return Optional.empty();
            }

            try {
                ((com.voxelbridge.mixin.MapInstanceInvoker) (Object) instance).voxelbridge$forceUpload();
            } catch (Exception ignored) {
            }
            DynamicTexture tex = ((com.voxelbridge.mixin.MapInstanceAccessor) (Object) instance).voxelbridge$getTexture();
            if (tex == null) {
                return Optional.empty();
            }
            NativeImage pixels = tex.getPixels();
            if (pixels == null) {
                return Optional.empty();
            }
            NativeImage copy = copyNativeImage(pixels);
            CACHE.put(location, copy);
            return Optional.of(copy);
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private static int parseMapId(ResourceLocation location) {
        if (location == null) {
            return -1;
        }
        String path = location.getPath();
        if (path == null) {
            return -1;
        }
        if (path.startsWith("map/")) {
            return parseInt(path.substring("map/".length()));
        }
        if (path.startsWith("maps/")) {
            return parseInt(path.substring("maps/".length()));
        }
        if (path.startsWith("textures/dynamic/map/")) {
            String file = path.substring("textures/dynamic/map/".length());
            int dot = file.indexOf('.');
            if (dot > 0) {
                file = file.substring(0, dot);
            }
            int underscore = file.indexOf('_');
            String idStr = underscore > 0 ? file.substring(0, underscore) : file;
            return parseInt(idStr);
        }
        return -1;
    }

    private static int parseInt(String value) {
        if (value == null || value.isEmpty()) {
            return -1;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static NativeImage copyNativeImage(NativeImage src) {
        NativeImage dst = new NativeImage(src.format(), src.getWidth(), src.getHeight(), false);
        dst.copyFrom(src);
        return dst;
    }
}
