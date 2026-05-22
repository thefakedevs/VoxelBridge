package com.voxelbridge.adapter;

import com.mojang.blaze3d.platform.NativeImage;
import com.voxelbridge.compat.ForgeTextureAccess;
import com.voxelbridge.export.texture.DynamicTextureUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.io.File;
import java.io.FileInputStream;
import java.util.Optional;

public final class ForgeDynamicTextureReader extends AbstractDynamicTextureReader {
    public static final ForgeDynamicTextureReader INSTANCE = new ForgeDynamicTextureReader();

    private ForgeDynamicTextureReader() {
        super("Forge/1.20.1");
    }

    public static void touch() {
    }

    @Override
    protected void preheatMapTexture(ResourceLocation location) {
        int mapId = DynamicTextureUtil.parseMapId(location);
        if (mapId < 0) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        var mapRenderer = mc.gameRenderer.getMapRenderer();
        if (mapRenderer == null) {
            return;
        }
        try {
            MapItemSavedData data = MapItem.getSavedData(mapId, mc.level);
            if (data != null) {
                mapRenderer.update(mapId, data);
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    protected Optional<NativeImage> loadMapPixels(ResourceLocation location) {
        int mapId = DynamicTextureUtil.parseMapId(location);
        if (mapId < 0) {
            return Optional.empty();
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return Optional.empty();
        }
        var mapRenderer = mc.gameRenderer.getMapRenderer();
        if (mapRenderer == null) {
            return Optional.empty();
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
            DynamicTexture texture = ((com.voxelbridge.mixin.MapInstanceAccessor) (Object) instance).voxelbridge$getTexture();
            NativeImage pixels = texture != null ? texture.getPixels() : null;
            return pixels != null ? Optional.of(pixels) : Optional.empty();
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    @Override
    protected NativeImage getDynamicTexturePixels(AbstractTexture texture) {
        return ForgeTextureAccess.getDynamicTexturePixels(texture);
    }

    @Override
    protected Optional<NativeImage> loadHttpTexture(AbstractTexture texture) {
        File file = ForgeTextureAccess.getHttpTextureFile(texture);
        if (file != null && file.exists()) {
            try {
                return Optional.of(NativeImage.read(new FileInputStream(file)));
            } catch (Exception ignored) {
            }
        }
        return Optional.empty();
    }
}
