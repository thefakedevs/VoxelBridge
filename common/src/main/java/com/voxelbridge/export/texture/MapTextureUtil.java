package com.voxelbridge.export.texture;

import com.voxelbridge.export.exporter.resolve.ResolvedTexture;
import com.voxelbridge.platform.render.RenderTypeTextureResolver;
import com.voxelbridge.util.debug.LogModule;
import com.voxelbridge.util.debug.VoxelBridgeLogger;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapId;

/**
 * Shared utilities for dynamic map textures (item frames, render types, and resource keys).
 * Kept in common to avoid duplicating map parsing logic across platforms.
 */
public final class MapTextureUtil {

    private MapTextureUtil() {}

    public static ResolvedTexture resolveItemFrameMap(ItemFrame frame, RenderType type) {
        if (frame == null) {
            return null;
        }
        ItemStack stack = frame.getItem();
        if (stack == null || !(stack.getItem() instanceof MapItem)) {
            debug("ItemFrame stack is not MapItem.");
            return null;
        }
        if (type != null && !isMapRenderType(type)) {
            debug("ItemFrame render type is not map render type.");
            return null;
        }
        int mapId = getMapIdFromStack(stack);
        if (mapId < 0) {
            debug("ItemFrame map id not found.");
            return null;
        }
        ResourceLocation mapLoc = ResourceLocation.fromNamespaceAndPath("minecraft", "map/" + mapId);
        debug("Resolved ItemFrame map: id=" + mapId + " loc=" + mapLoc);
        return new ResolvedTexture(mapLoc, 0f, 1f, 0f, 1f, false, null, null);
    }

    public static boolean isMapRenderType(RenderType type) {
        if (type == null) {
            return false;
        }
        try {
            ResourceLocation tex = RenderTypeTextureResolver.INSTANCE.resolve(type);
            if (tex == null) {
                debug("RenderType resolved texture is null.");
                return false;
            }
            String path = tex.getPath();
            debug("RenderType resolved texture: " + tex);
            return isMapPath(path);
        } catch (Exception ignored) {
            return false;
        }
    }

    public static int getMapIdFromStack(ItemStack stack) {
        try {
            MapId id = stack.get(DataComponents.MAP_ID);
            if (id != null) {
                debug("MapId from stack: " + id.id());
                return id.id();
            }
        } catch (Exception ignored) {
        }
        return -1;
    }

    public static ResourceLocation normalizeDynamicMapLocation(ResourceLocation location) {
        if (location == null) {
            return null;
        }
        int id = parseMapId(location);
        if (id < 0) {
            return null;
        }
        ResourceLocation normalized = ResourceLocation.fromNamespaceAndPath(location.getNamespace(), "map/" + id);
        debug("Normalize dynamic map location: " + location + " -> " + normalized);
        return normalized;
    }

    public static int parseMapId(ResourceLocation location) {
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
        if (path.startsWith("textures/map/")) {
            String file = path.substring("textures/map/".length());
            int dot = file.indexOf('.');
            if (dot > 0) {
                file = file.substring(0, dot);
            }
            return parseInt(file);
        }
        if (path.startsWith("textures/maps/")) {
            String file = path.substring("textures/maps/".length());
            int dot = file.indexOf('.');
            if (dot > 0) {
                file = file.substring(0, dot);
            }
            return parseInt(file);
        }
        return -1;
    }

    private static boolean isMapPath(String path) {
        if (path == null) {
            return false;
        }
        return path.startsWith("map/")
            || path.startsWith("maps/")
            || path.startsWith("textures/dynamic/map/")
            || path.startsWith("dynamic/map/")
            || path.startsWith("textures/map/")
            || path.startsWith("textures/maps/");
    }

    private static void debug(String message) {
        if (VoxelBridgeLogger.isDebugEnabled(LogModule.DYNAMIC_MAP)) {
            VoxelBridgeLogger.debug(LogModule.DYNAMIC_MAP, "[MapTextureUtil] " + message);
        }
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
}
