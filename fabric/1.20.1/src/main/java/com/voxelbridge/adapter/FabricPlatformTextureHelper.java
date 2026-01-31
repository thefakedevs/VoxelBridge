package com.voxelbridge.adapter;

import com.mojang.blaze3d.platform.NativeImage;
import com.voxelbridge.compat.FabricAtlasAccess;
import com.voxelbridge.compat.FabricPaintingAccess;
import com.voxelbridge.compat.FabricSpriteAccess;
import com.voxelbridge.compat.FabricTextureAccess;
import com.voxelbridge.export.exporter.resolve.ResolvedTexture;
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
        return img.getPixelRGBA(x, y);
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

        // 1) Dynamic / HTTP textures (render thread)
        if (com.mojang.blaze3d.systems.RenderSystem.isOnRenderThread()) {
            var tm = Minecraft.getInstance().getTextureManager();
            var texture = tm.getTexture(location);

            NativeImage pixels = FabricTextureAccess.getDynamicTexturePixels(texture);
            if (pixels != null) {
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
                ResourceLocation spriteName = sprite.contents() != null ? sprite.contents().name() : resolvePaintingId(variantHolder);
                spriteName = normalizePaintingSpriteName(spriteName);
                return new ResolvedTexture(
                    spriteName,
                    sprite.getU0(), sprite.getU1(), sprite.getV0(), sprite.getV1(),
                    true,
                    sprite,
                    sprite.atlasLocation());
            }

            // Fallback to file path
            ResourceLocation assetId = resolvePaintingId(variantHolder);
            if (assetId == null) {
                return null;
            }
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
            ResourceLocation textureLoc = new ResourceLocation(assetId.getNamespace(), path);
            return new ResolvedTexture(textureLoc, 0f, 1f, 0f, 1f, false, null, null);
        } catch (Exception e) {
            VoxelBridgeLogger.warn(LogModule.ENTITY, "Failed to resolve painting texture via platform helper: " + e);
            return null;
        }
    }

    private ResolvedTexture resolveItemFrame(ItemFrame frame) {
        try {
            ResourceLocation woodLoc = new ResourceLocation("minecraft", "block/birch_planks");
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
        ResourceLocation loc = new ResourceLocation("minecraft", path);
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
        return new ResourceLocation(spriteName.getNamespace(), "painting/" + path);
    }

    private static ResourceLocation resolvePaintingId(Holder<PaintingVariant> holder) {
        if (holder == null) {
            return null;
        }
        return holder.unwrapKey().map(key -> key.location()).orElse(null);
    }
}

