package com.voxelbridge.export.exporter.blockentity;

import com.voxelbridge.export.ExportContext;
import com.voxelbridge.export.texture.EntityTextureManager;
import com.voxelbridge.core.util.color.ColorUtil;
import com.voxelbridge.core.util.image.ImageUtil;
import com.voxelbridge.util.debug.VoxelBridgeLogger;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.BannerBlockEntity;

import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
final class BannerTextureBaker {
    private static final ResourceLocation FLAG_ONLY_TEXTURE = new ResourceLocation("minecraft", "entity/banner/base");
    private static final ResourceLocation BASE_WITH_POLE_TEXTURE = new ResourceLocation("minecraft", "entity/banner_base");
    private static final float[] NO_TINT = new float[]{1.0f, 1.0f, 1.0f};

    private BannerTextureBaker() {
    }

    static BannerTextures bake(ExportContext ctx, BannerBlockEntity banner) {
        String key = BannerTextureBaker.buildKey(banner);
        String bakedPath = resolveOutputDir(ctx) + "/" + BannerTextureBaker.safe(key) + ".png";
        if (VoxelBridgeLogger.isDebugEnabled(com.voxelbridge.util.debug.LogModule.BLOCKENTITY)) {
            VoxelBridgeLogger.debug(com.voxelbridge.util.debug.LogModule.BLOCKENTITY, "[BannerBake] key=" + key + " path=" + bakedPath);
        }
        BufferedImage bakedImage = ctx.getGeneratedEntityTextures().computeIfAbsent(key, k -> BannerTextureBaker.composeTexture(ctx, banner));
        EntityTextureManager.TextureHandle bakedHandle = EntityTextureManager.registerGenerated(ctx, key, bakedPath, bakedImage);

        BannerTextureOverrides overrides = new BannerTextureOverrides();
        overrides.setBakedHandle(bakedHandle);
        ResourceLocation bannerBaseTexture = ModelBakery.BANNER_BASE.texture();
        overrides.map(bannerBaseTexture, bakedHandle);
        overrides.map(BASE_WITH_POLE_TEXTURE, bakedHandle);
        ResourceLocation sheetsBannerBaseTexture = getSheetsBannerBaseTexture();
        if (sheetsBannerBaseTexture != null) {
            overrides.map(sheetsBannerBaseTexture, bakedHandle);
        }
        ResourceLocation altBase1 = new ResourceLocation("minecraft", "entity/banner_base");
        ResourceLocation altBase2 = new ResourceLocation("minecraft", "entity/banner/base");
        overrides.map(altBase1, bakedHandle);
        overrides.map(altBase2, bakedHandle);
        List<Layer> layers = getLayers(banner);
        if (VoxelBridgeLogger.isDebugEnabled(com.voxelbridge.util.debug.LogModule.BLOCKENTITY)) {
            VoxelBridgeLogger.debug(com.voxelbridge.util.debug.LogModule.BLOCKENTITY, "[BannerBake] layers=" + layers.size());
        }
        for (Layer layer : layers) {
            ResourceLocation sprite = resolveBannerSprite(layer.pattern);
            if (sprite == null) {
                if (VoxelBridgeLogger.isDebugEnabled(com.voxelbridge.util.debug.LogModule.BLOCKENTITY)) {
                    VoxelBridgeLogger.debug(com.voxelbridge.util.debug.LogModule.BLOCKENTITY, "[BannerBake] sprite=null pattern=" + debugPattern(layer.pattern));
                }
                continue;
            }
            if (VoxelBridgeLogger.isDebugEnabled(com.voxelbridge.util.debug.LogModule.BLOCKENTITY)) {
                VoxelBridgeLogger.debug(com.voxelbridge.util.debug.LogModule.BLOCKENTITY, "[BannerBake] sprite=" + sprite + " color=" + (layer.color != null ? layer.color.getSerializedName() : "none"));
            }
            if (isBaseSprite(sprite)) {
                overrides.map(sprite, bakedHandle);
            } else {
                overrides.mapAndSkip(sprite, bakedHandle);
            }
            String patternPath = sprite.getPath();
            if (patternPath.contains("/")) {
                String altPath = patternPath.replace("entity/banner/", "entity/banner_");
                ResourceLocation altSprite = new ResourceLocation(sprite.getNamespace(), altPath);
                if (isBaseSprite(altSprite)) {
                    overrides.map(altSprite, bakedHandle);
                } else {
                    overrides.mapAndSkip(altSprite, bakedHandle);
                }
            }
        }
        return new BannerTextures(bakedHandle, overrides);
    }

    private static BufferedImage composeTexture(ExportContext ctx, BannerBlockEntity banner) {
        BufferedImage base = ImageUtil.copyOrBlank(BannerTextureBaker.loadSprite(ctx, BASE_WITH_POLE_TEXTURE), 64, 64);
        BufferedImage result = ImageUtil.copy(base);
        BannerTextureBaker.applyTinted(ctx, result, FLAG_ONLY_TEXTURE, banner.getBaseColor());
        List<Layer> layers = getLayers(banner);
        for (Layer layer : layers) {
            ResourceLocation sprite = resolveBannerSprite(layer.pattern);
            if (sprite == null) {
                continue;
            }
            BannerTextureBaker.applyTinted(ctx, result, sprite, layer.color);
        }
        return result;
    }

    private static void applyTinted(ExportContext ctx, BufferedImage target, ResourceLocation sprite, DyeColor color) {
        BufferedImage texture = BannerTextureBaker.loadSprite(ctx, sprite);
        if (texture == null) {
            return;
        }

        // Resource packs may change grayscale size; scale to target before tinting/compositing.
        int targetW = target.getWidth();
        int targetH = target.getHeight();
        if (texture.getWidth() != targetW || texture.getHeight() != targetH) {
            texture = ImageUtil.scaleNearest(texture, targetW, targetH);
        }

        float[] mul = color != null ? color.getTextureDiffuseColors() : NO_TINT;
        int w = targetW;
        int h = targetH;
        for (int y = 0; y < h; ++y) {
            for (int x = 0; x < w; ++x) {
                int argb = texture.getRGB(x, y);
                int a = argb >>> 24 & 0xFF;
                if (a == 0) continue;
                int r = (int)((float)(argb >>> 16 & 0xFF) * mul[0]);
                int g = (int)((float)(argb >>> 8 & 0xFF) * mul[1]);
                int b = (int)((float)(argb & 0xFF) * mul[2]);
                int tinted = a << 24 | ImageUtil.clampChannel(r) << 16 | ImageUtil.clampChannel(g) << 8 | ImageUtil.clampChannel(b);
                int out = ImageUtil.alphaBlend(target.getRGB(x, y), tinted);
                target.setRGB(x, y, out);
            }
        }
    }

    private static BufferedImage loadSprite(ExportContext ctx, ResourceLocation sprite) {
        String resourceKey = ctx.getTextureAccess().spriteKeyToResourceKey(sprite.toString());
        return ctx.getTextureAccess().readTexture(resourceKey);
    }

    private static String buildKey(BannerBlockEntity banner) {
        StringBuilder sb = new StringBuilder("base:");
        sb.append(banner.getBaseColor().getSerializedName());
        int index = 0;
        for (Layer layer : getLayers(banner)) {
            ResourceLocation id = resolvePatternId(layer.pattern);
            String colorName = layer.color != null ? layer.color.getSerializedName() : "none";
            sb.append("__").append(index++).append(":").append(id).append("@").append(colorName);
        }
        return sb.toString();
    }

    private static String resolveOutputDir(ExportContext ctx) {
        return com.voxelbridge.config.ExportRuntimeConfig.getAtlasMode()
            == com.voxelbridge.config.ExportRuntimeConfig.AtlasMode.INDIVIDUAL
            ? "textures/individual"
            : "entity_textures/banner";
    }

    private static String safe(String s) {
        String sanitized = BannerTextureBaker.sanitize(s);
        if (sanitized.length() <= 80) {
            return sanitized;
        }
        return sanitized.substring(0, Math.min(40, sanitized.length())) + "_" + BannerTextureBaker.sha1Hex(s).substring(0, 16);
    }

    private static String sanitize(String input) {
        StringBuilder sb = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); ++i) {
            char c = Character.toLowerCase(input.charAt(i));
            if (c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '.' || c == '-' || c == '_') {
                sb.append(c);
            } else {
                sb.append('_');
            }
        }
        if (sb.isEmpty()) {
            sb.append("banner");
        }
        return sb.toString();
    }

    private static String sha1Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit(b >> 4 & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 not available", e);
        }
    }

    private static final class BannerTextureOverrides implements TextureOverrideMap {
        private final Map<ResourceLocation, EntityTextureManager.TextureHandle> overrides = new HashMap<>();
        private final Set<ResourceLocation> skipSprites = new HashSet<>();
        private EntityTextureManager.TextureHandle bakedHandle;

        void setBakedHandle(EntityTextureManager.TextureHandle handle) {
            this.bakedHandle = handle;
        }

        void map(ResourceLocation sprite, EntityTextureManager.TextureHandle handle) {
            this.overrides.put(sprite, handle);
        }

        void mapAndSkip(ResourceLocation sprite, EntityTextureManager.TextureHandle handle) {
            this.map(sprite, handle);
            this.skipSprite(sprite);
        }

        void skipSprite(ResourceLocation sprite) {
            this.skipSprites.add(sprite);
        }

        @Override
        public EntityTextureManager.TextureHandle resolve(ResourceLocation spriteName) {
            EntityTextureManager.TextureHandle mapped = this.overrides.get(spriteName);
            if (mapped != null) {
                return mapped;
            }
            return this.bakedHandle;
        }

        @Override
        public boolean skipQuad(ResourceLocation spriteName, float[] localU, float[] localV) {
            return spriteName != null && this.skipSprites.contains(spriteName);
        }
    }

    record BannerTextures(EntityTextureManager.TextureHandle bakedHandle, TextureOverrideMap overrides) {
    }

    private static final class Layer {
        private final Object pattern;
        private final DyeColor color;

        private Layer(Object pattern, DyeColor color) {
            this.pattern = pattern;
            this.color = color;
        }
    }

    private static List<Layer> getLayers(BannerBlockEntity banner) {
        List<Layer> layers = new ArrayList<>();
        if (banner == null) {
            return layers;
        }
        Object raw;
        try {
            raw = banner.getPatterns();
        } catch (Exception ignored) {
            return layers;
        }
        Object maybeLayers = tryInvoke(raw, "layers");
        if (maybeLayers instanceof Iterable<?> iterableLayers) {
            for (Object entry : iterableLayers) {
                Object pattern = tryInvoke(entry, "pattern");
                DyeColor color = (DyeColor) tryInvoke(entry, "color");
                if (pattern == null && color == null) {
                    pattern = tryInvoke(entry, "getFirst");
                    color = (DyeColor) tryInvoke(entry, "getSecond");
                }
                layers.add(new Layer(pattern, color));
            }
            return layers;
        }
        if (raw instanceof Iterable<?> iterable) {
            for (Object entry : iterable) {
                Object pattern = tryInvoke(entry, "getFirst");
                DyeColor color = (DyeColor) tryInvoke(entry, "getSecond");
                if (pattern == null && color == null) {
                    pattern = tryInvoke(entry, "getLeft");
                    color = (DyeColor) tryInvoke(entry, "getRight");
                }
                layers.add(new Layer(pattern, color));
            }
        }
        return layers;
    }

    private static ResourceLocation resolveBannerSprite(Object pattern) {
        if (pattern == null) {
            return null;
        }
        if (VoxelBridgeLogger.isDebugEnabled(com.voxelbridge.util.debug.LogModule.BLOCKENTITY)) {
            VoxelBridgeLogger.debug(com.voxelbridge.util.debug.LogModule.BLOCKENTITY, "[BannerBake] resolve sprite for pattern=" + debugPattern(pattern));
        }
        ResourceLocation texture = resolveBannerSpriteFromPattern(pattern);
        if (texture != null) {
            return texture;
        }
        Object unwrapped = unwrapPattern(pattern);
        if (unwrapped != null && unwrapped != pattern) {
            if (VoxelBridgeLogger.isDebugEnabled(com.voxelbridge.util.debug.LogModule.BLOCKENTITY)) {
                VoxelBridgeLogger.debug(com.voxelbridge.util.debug.LogModule.BLOCKENTITY, "[BannerBake] unwrapped pattern=" + debugPattern(unwrapped));
            }
            texture = resolveBannerSpriteFromPattern(unwrapped);
            if (texture != null) {
                return texture;
            }
        }
        ResourceLocation id = resolvePatternId(pattern);
        if (id != null) {
            return new ResourceLocation(id.getNamespace(), "entity/banner/" + id.getPath());
        }
        return null;
    }

    private static ResourceLocation resolvePatternId(Object pattern) {
        if (pattern == null) {
            return new ResourceLocation("minecraft", "unknown");
        }
        if (pattern instanceof ResourceLocation rl) {
            return rl;
        }
        Object location = tryInvoke(pattern, "location");
        if (location instanceof ResourceLocation rl) {
            return rl;
        }
        Object registryName = tryInvoke(pattern, "getRegistryName");
        if (registryName instanceof ResourceLocation rl) {
            return rl;
        }
        Object key = tryInvoke(pattern, "getKey");
        Object keyLoc = tryInvoke(key, "location");
        if (keyLoc instanceof ResourceLocation rl) {
            return rl;
        }
        Object unwrapKey = tryInvoke(pattern, "unwrapKey");
        Object unwrapLoc = tryInvoke(unwrapKey, "location");
        if (unwrapLoc instanceof ResourceLocation rl) {
            return rl;
        }
        Object registryKey = tryInvoke(pattern, "registryKey");
        Object registryLoc = tryInvoke(registryKey, "location");
        if (registryLoc instanceof ResourceLocation rl) {
            return rl;
        }
        Object holderKey = tryInvoke(pattern, "key");
        Object holderLoc = tryInvoke(holderKey, "location");
        if (holderLoc instanceof ResourceLocation rl) {
            return rl;
        }
        ResourceLocation parsed = parsePatternIdFromToString(pattern);
        if (parsed != null) {
            return parsed;
        }
        return new ResourceLocation("minecraft", "unknown");
    }

    private static ResourceLocation getSheetsBannerBaseTexture() {
        try {
            java.lang.reflect.Field field = Sheets.class.getDeclaredField("BANNER_BASE");
            field.setAccessible(true);
            Object material = field.get(null);
            Object texture = tryInvoke(material, "texture");
            return texture instanceof ResourceLocation ? (ResourceLocation) texture : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static ResourceLocation resolveBannerSpriteFromPattern(Object pattern) {
        try {
            java.lang.reflect.Method[] methods = Sheets.class.getMethods();
            for (java.lang.reflect.Method method : methods) {
                if (!"getBannerMaterial".equals(method.getName())) {
                    continue;
                }
                Class<?>[] params = method.getParameterTypes();
                if (params.length != 1) {
                    continue;
                }
                if (!params[0].isInstance(pattern)) {
                    continue;
                }
                Object material = method.invoke(null, pattern);
                Object texture = tryInvoke(material, "texture");
                return texture instanceof ResourceLocation ? (ResourceLocation) texture : null;
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private static Object unwrapPattern(Object pattern) {
        Object viaOptional = unwrapOptional(pattern);
        if (viaOptional != null) {
            return viaOptional;
        }
        Object value = tryInvoke(pattern, "value");
        if (value != null) {
            return value;
        }
        value = tryInvoke(pattern, "get");
        if (value != null) {
            return value;
        }
        Object holder = tryInvoke(pattern, "unwrap");
        if (holder != null) {
            return holder;
        }
        return pattern;
    }

    private static Object tryInvoke(Object target, String methodName) {
        if (target == null) {
            return null;
        }
        try {
            var method = target.getClass().getMethod(methodName);
            method.setAccessible(true);
            return method.invoke(target);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String debugPattern(Object pattern) {
        if (pattern == null) {
            return "null";
        }
        return pattern.getClass().getName() + ":" + pattern;
    }

    private static boolean isBaseSprite(ResourceLocation sprite) {
        if (sprite == null) {
            return false;
        }
        return sprite.getPath().endsWith("entity/banner/base")
            || sprite.getPath().endsWith("entity/banner_base");
    }

    private static Object unwrapOptional(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof java.util.Optional<?> optional) {
            return optional.orElse(null);
        }
        return null;
    }

    private static ResourceLocation parsePatternIdFromToString(Object pattern) {
        if (pattern == null) {
            return null;
        }
        String s = pattern.toString();
        int keyStart = s.indexOf("ResourceKey[");
        if (keyStart < 0) {
            return null;
        }
        int slash = s.indexOf(" / ", keyStart);
        int end = s.indexOf("]", keyStart);
        if (slash < 0 || end < 0 || end <= slash + 3) {
            return null;
        }
        String id = s.substring(slash + 3, end).trim();
        if (id.isEmpty()) {
            return null;
        }
        int colon = id.indexOf(':');
        if (colon > 0) {
            return new ResourceLocation(id.substring(0, colon), id.substring(colon + 1));
        }
        return new ResourceLocation("minecraft", id);
    }
}
