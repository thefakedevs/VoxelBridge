package com.voxelbridge.adapter;

import com.mojang.blaze3d.platform.NativeImage;
import com.voxelbridge.export.texture.DynamicTextureUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.Optional;

public final class NeoForgeDynamicTextureReader extends AbstractDynamicTextureReader {

    public static final NeoForgeDynamicTextureReader INSTANCE = new NeoForgeDynamicTextureReader();

    private NeoForgeDynamicTextureReader() {
        super("NeoForge/1.21.8");
    }

    @Override
    protected void preheatMapTexture(ResourceLocation location) {
        int mapId = DynamicTextureUtil.parseMapId(location);
        if (mapId < 0) {
            return;
        }
        MapId id = new MapId(mapId);
        Minecraft mc = Minecraft.getInstance();
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
                debug("MapTextureManager.update: " + id);
                mapTextureManager.update(id, data);
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    protected Optional<NativeImage> loadMapPixels(ResourceLocation location) {
        return Optional.empty();
    }

    @Override
    protected NativeImage getDynamicTexturePixels(AbstractTexture texture) {
        if (texture instanceof DynamicTexture dt) {
            return dt.getPixels();
        }
        return null;
    }

    @Override
    protected Optional<NativeImage> loadHttpTexture(AbstractTexture texture) {
        return Optional.empty();
    }
}
