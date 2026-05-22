package com.voxelbridge.adapter;

import com.mojang.blaze3d.platform.NativeImage;
import com.voxelbridge.compat.ForgeAtlasAccess;
import com.voxelbridge.compat.ForgeSpriteAccess;
import com.voxelbridge.export.exporter.resolve.ResolvedTexture;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;

import java.util.Collection;
import java.util.Optional;

public final class ForgePlatformTextureHelper implements PlatformTextureHelper {
    @Override
    public int getPixelRgba(NativeImage img, int x, int y) {
        return img != null ? img.getPixelRGBA(x, y) : 0;
    }

    @Override
    public NativeImage getOriginalImage(TextureAtlasSprite sprite) {
        return ForgeSpriteAccess.getOriginalImage(sprite);
    }

    @Override
    public Collection<TextureAtlasSprite> getAllSprites(TextureAtlas atlas) {
        return ForgeAtlasAccess.getAllSprites(atlas);
    }

    @Override
    public Optional<NativeImage> readTexture(ResourceLocation location) {
        return ForgeDynamicTextureReader.INSTANCE.readTexture(location);
    }

    @Override
    public void copyNativeImage(NativeImage src, NativeImage dst) {
        if (src != null && dst != null) {
            dst.copyFrom(src);
        }
    }

    @Override
    public ResolvedTexture resolveEntityTexture(Entity entity, RenderType type) {
        if (entity instanceof Painting painting) {
            return resolvePainting(painting);
        }
        if (entity instanceof ItemFrame frame) {
            return resolveItemFrame(frame, type);
        }
        return null;
    }

    private ResolvedTexture resolvePainting(Painting painting) {
        try {
            Holder<PaintingVariant> variantHolder = painting.getVariant();
            PaintingVariant variant = variantHolder.value();
            TextureAtlasSprite sprite = Minecraft.getInstance().getPaintingTextures().get(variant);
            if (sprite != null) {
                ResourceLocation spriteName = sprite.contents() != null ? sprite.contents().name() : resolvePaintingId(variantHolder);
                spriteName = normalizePaintingSpriteName(spriteName);
                return new ResolvedTexture(spriteName, sprite.getU0(), sprite.getU1(), sprite.getV0(), sprite.getV1(), true, sprite, sprite.atlasLocation());
            }
            ResourceLocation assetId = resolvePaintingId(variantHolder);
            if (assetId == null) {
                return null;
            }
            String path = assetId.getPath();
            if (!path.startsWith("textures/")) {
                path = path.startsWith("painting/") ? "textures/" + path : "textures/painting/" + path;
            }
            if (!path.endsWith(".png")) {
                path = path + ".png";
            }
            return new ResolvedTexture(new ResourceLocation(assetId.getNamespace(), path), 0f, 1f, 0f, 1f, false, null, null);
        } catch (Exception ignored) {
            return null;
        }
    }

    private ResolvedTexture resolveItemFrame(ItemFrame frame, RenderType type) {
        ResolvedTexture map = resolveItemFrameMap(frame, type);
        if (map != null || type != null) {
            return map;
        }
        try {
            ResourceLocation woodLoc = new ResourceLocation("minecraft", "block/birch_planks");
            TextureAtlasSprite sprite = Minecraft.getInstance().getModelManager().getAtlas(TextureAtlas.LOCATION_BLOCKS).getSprite(woodLoc);
            if (sprite != null) {
                ResourceLocation spriteName = sprite.contents() != null ? sprite.contents().name() : woodLoc;
                return new ResolvedTexture(spriteName, sprite.getU0(), sprite.getU1(), sprite.getV0(), sprite.getV1(), true, sprite, sprite.atlasLocation());
            }
        } catch (Exception ignored) {
        }
        boolean isGlow = frame instanceof net.minecraft.world.entity.decoration.GlowItemFrame;
        String path = isGlow ? "textures/entity/glow_item_frame.png" : "textures/entity/item_frame.png";
        return new ResolvedTexture(new ResourceLocation("minecraft", path), 0f, 1f, 0f, 1f, false, null, null);
    }

    private ResolvedTexture resolveItemFrameMap(ItemFrame frame, RenderType type) {
        ItemStack stack = frame.getItem();
        if (stack == null || !(stack.getItem() instanceof MapItem) || !isMapRenderType(type)) {
            return null;
        }
        int mapId = getMapIdFromStack(stack);
        return mapId >= 0 ? new ResolvedTexture(new ResourceLocation("minecraft", "map/" + mapId), 0f, 1f, 0f, 1f, false, null, null) : null;
    }

    private static boolean isMapRenderType(RenderType type) {
        if (type == null) {
            return false;
        }
        ResourceLocation tex = Adapters.getPlatformRenderHelper().getRenderTypeTexture(type);
        if (tex == null) {
            return false;
        }
        String path = tex.getPath();
        return path.startsWith("textures/dynamic/map/") || path.startsWith("maps/");
    }

    private static int getMapIdFromStack(ItemStack stack) {
        try {
            var tag = stack.getTag();
            if (tag != null && tag.contains("map", 3)) {
                return tag.getInt("map");
            }
        } catch (Exception ignored) {
        }
        return -1;
    }

    private static ResourceLocation normalizePaintingSpriteName(ResourceLocation spriteName) {
        if (spriteName == null) {
            return null;
        }
        String path = spriteName.getPath();
        return path.startsWith("textures/painting/") || path.startsWith("painting/")
                ? spriteName
                : new ResourceLocation(spriteName.getNamespace(), "painting/" + path);
    }

    private static ResourceLocation resolvePaintingId(Holder<PaintingVariant> holder) {
        return holder != null ? holder.unwrapKey().map(key -> key.location()).orElse(null) : null;
    }
}
