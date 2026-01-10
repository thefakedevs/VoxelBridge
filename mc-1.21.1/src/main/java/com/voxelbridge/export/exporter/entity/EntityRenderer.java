package com.voxelbridge.export.exporter.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.voxelbridge.core.ir.IrSink;
import com.voxelbridge.core.ir.RenderLayer;
import com.voxelbridge.config.ExportRuntimeConfig;
import com.voxelbridge.export.ExportContext;
import com.voxelbridge.export.exporter.resolve.AtlasLocator;
import com.voxelbridge.export.exporter.resolve.RenderTypeResolver;
import com.voxelbridge.export.exporter.resolve.ResolvedTexture;
import com.voxelbridge.export.exporter.resolve.TextureResolver;
import com.voxelbridge.export.texture.EntityTextureManager;
import com.voxelbridge.platform.client.ClientAccessHolder;
import com.voxelbridge.platform.render.RenderTypeTextureResolver;
import com.voxelbridge.platform.render.capture.CaptureBufferBase;
import com.voxelbridge.platform.render.capture.RenderCapture;
import com.voxelbridge.platform.render.capture.RenderCaptureUtil;
import com.voxelbridge.platform.texture.TextureLoader;
import com.voxelbridge.util.debug.LogModule;
import com.voxelbridge.util.debug.VoxelBridgeLogger;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Captures entity renderer output into an IR sink.
 */
@OnlyIn(Dist.CLIENT)
public final class EntityRenderer {

    private static final float[] EMPTY_UV = new float[8];
    private static final float[] NORMAL_UP = new float[] {
        0f, 1f, 0f,
        0f, 1f, 0f,
        0f, 1f, 0f,
        0f, 1f, 0f
    };
    private static AtlasLocator ATLAS_LOCATOR = new EntityAtlasLocator(ClientAccessHolder.get());
    private static TextureResolver<Entity> TEXTURE_RESOLVER = EntityTextureResolver.INSTANCE;
    private static RenderTypeResolver RENDER_TYPE_RESOLVER = RenderTypeTextureResolver.INSTANCE;
    private static final Set<String> LOGGED_MAP_RENDERERS = ConcurrentHashMap.newKeySet();

    private EntityRenderer() {}

    public static void setAtlasLocator(AtlasLocator locator) {
        if (locator != null) {
            ATLAS_LOCATOR = locator;
        }
    }

    public static void setTextureResolver(TextureResolver<Entity> resolver) {
        if (resolver != null) {
            TEXTURE_RESOLVER = resolver;
        }
    }

    public static void setRenderTypeResolver(RenderTypeResolver resolver) {
        if (resolver != null) {
            RENDER_TYPE_RESOLVER = resolver;
        }
    }

    public static boolean render(
        ExportContext ctx,
        Entity entity,
        IrSink sceneSink,
        double offsetX,
        double offsetY,
        double offsetZ
    ) {
        return renderInternal(ctx, entity, sceneSink, offsetX, offsetY, offsetZ, true);
    }

    public static boolean renderOnMainThread(
        ExportContext ctx,
        Entity entity,
        IrSink sceneSink,
        double offsetX,
        double offsetY,
        double offsetZ
    ) {
        return renderInternal(ctx, entity, sceneSink, offsetX, offsetY, offsetZ, false);
    }

    private static boolean renderInternal(
        ExportContext ctx,
        Entity entity,
        IrSink sceneSink,
        double offsetX,
        double offsetY,
        double offsetZ,
        boolean scheduleOnMainThread
    ) {
        try {
            VoxelBridgeLogger.debug(LogModule.ENTITY, "[EntityRenderer] Starting render for " + entity.getType());
            if (VoxelBridgeLogger.isDebugEnabled(LogModule.ENTITY)) {
                net.minecraft.world.phys.Vec3 pos = entity.position();
                VoxelBridgeLogger.debug(LogModule.ENTITY, String.format(
                    "[Position] %s at world[%.3f, %.3f, %.3f] offset[%.3f, %.3f, %.3f] final[%.3f, %.3f, %.3f]",
                    entity.getType(),
                    pos.x, pos.y, pos.z,
                    offsetX, offsetY, offsetZ,
                    pos.x + offsetX, pos.y + offsetY, pos.z + offsetZ));
            }

            EntityRenderDispatcher dispatcher = ctx.getMc().getEntityRenderDispatcher();
            net.minecraft.client.renderer.entity.EntityRenderer<? super Entity> renderer =
                    dispatcher.getRenderer(entity);
            if (renderer == null) {
                VoxelBridgeLogger.error(LogModule.ENTITY, String.format(
                    "[ERROR] %s at [%.2f, %.2f, %.2f] - %s",
                    entity.getType(),
                    entity.position().x,
                    entity.position().y,
                    entity.position().z,
                    "No renderer available"));
                return false;
            }

            VoxelBridgeLogger.debug(LogModule.ENTITY, "[EntityRenderer] Using renderer: " + renderer.getClass().getSimpleName());

            PoseStack poseStack = new PoseStack();

            // Calculate base position
            double finalX = entity.getX() + offsetX;
            double finalY = entity.getY() + offsetY;
            double finalZ = entity.getZ() + offsetZ;

            // Apply direction-based offset for hanging entities (paintings, item frames)
            if (entity instanceof net.minecraft.world.entity.decoration.HangingEntity hangingEntity) {
                double[] hangingOffset = HangingEntityPositionUtil.calculateRenderOffset(hangingEntity);
                finalX += hangingOffset[0];
                finalY += hangingOffset[1];
                finalZ += hangingOffset[2];

                VoxelBridgeLogger.debug(LogModule.ENTITY, String.format(
                    "[HangingEntity] Applied direction offset: direction=%s offset=[%.4f, %.4f, %.4f]",
                    hangingEntity.getDirection(), hangingOffset[0], hangingOffset[1], hangingOffset[2]));
            }

            poseStack.translate(finalX, finalY, finalZ);

            CaptureBuffer captureBuffer = new CaptureBuffer(ctx, sceneSink, offsetX, offsetY, offsetZ, entity);
            float partial = 0f;
            float yaw = entity.getYRot();
            if (VoxelBridgeLogger.isDebugEnabled(LogModule.ENTITY)) {
                VoxelBridgeLogger.debug(LogModule.ENTITY, String.format(
                    "[Rotation] %s yaw=%.2fdeg (actual=%.2fdeg, isHanging=%s)",
                    entity.getType(),
                    yaw,
                    entity.getYRot(),
                    entity instanceof net.minecraft.world.entity.decoration.HangingEntity));
            }

            // Use max light level for better visibility
            int packedLight = 0xF000F0;

            boolean[] renderCompleted = new boolean[1];
            Exception[] renderException = new Exception[1];

            Runnable renderCall = () -> {
                try {
                    warmMapTexture(ctx, entity);
                    var renderOffset = renderer.getRenderOffset(entity, partial);
                    poseStack.translate(renderOffset.x(), renderOffset.y(), renderOffset.z());

                    renderer.render(
                        entity,
                        yaw,
                        partial,
                        poseStack,
                        captureBuffer,
                        packedLight
                    );
                    renderCompleted[0] = true;
                } catch (Exception e) {
                    renderException[0] = e;
                }
            };

            if (scheduleOnMainThread) {
                ctx.runOnMainThread(renderCall);
            } else {
                renderCall.run();
            }

            if (renderException[0] != null) {
                VoxelBridgeLogger.error(LogModule.ENTITY, String.format(
                    "[ERROR] %s at [%.2f, %.2f, %.2f] - %s",
                    entity.getType(),
                    entity.position().x,
                    entity.position().y,
                    entity.position().z,
                    "Render exception: " + renderException[0].getMessage()), renderException[0]);
            }

            captureBuffer.flush();
            boolean hadGeometry = captureBuffer.hadGeometry();

            if (hadGeometry) {
                VoxelBridgeLogger.debug(LogModule.ENTITY, "[EntityRenderer] Successfully captured geometry for " + entity.getType());
            } else {
                VoxelBridgeLogger.warn(LogModule.ENTITY, String.format(
                    "[NoGeometry] %s at [%.2f, %.2f, %.2f] - %s",
                    entity.getType(),
                    entity.position().x,
                    entity.position().y,
                    entity.position().z,
                    "No vertices were captured during render"));
            }

            return hadGeometry;
        } catch (Exception e) {
            VoxelBridgeLogger.error(LogModule.ENTITY, String.format(
                "[ERROR] %s at [%.2f, %.2f, %.2f] - %s",
                entity.getType(),
                entity.position().x,
                entity.position().y,
                entity.position().z,
                "Unexpected error: " + e.getMessage()), e);
            return false;
        }
    }

    private static boolean isHangingEntity(Entity entity) {
        return entity instanceof net.minecraft.world.entity.decoration.HangingEntity;
    }

    private static void warmMapTexture(ExportContext ctx, Entity entity) {
        if (!(entity instanceof ItemFrame itemFrame)) {
            return;
        }
        ItemStack item = itemFrame.getItem();
        if (item == null || item.isEmpty()) {
            return;
        }
        if (!(item.getItem() instanceof MapItem)) {
            return;
        }
        MapItemSavedData data = MapItem.getSavedData(item, itemFrame.level());
        if (data == null) {
            VoxelBridgeLogger.warn(LogModule.DYNAMIC_MAP, "[DynamicMap] No MapItemSavedData for item frame");
            return;
        }
        Object mapRenderer = ctx.getMc().gameRenderer.getMapRenderer();
        if (mapRenderer == null) {
            VoxelBridgeLogger.warn(LogModule.DYNAMIC_MAP, "[DynamicMap] MapRenderer unavailable");
            return;
        }
        boolean updated = tryUpdateMapRenderer(mapRenderer, data, item);
        if (!updated) {
            VoxelBridgeLogger.warn(LogModule.DYNAMIC_MAP, "[DynamicMap] MapRenderer update() not found or failed");
        }
    }

    private static boolean tryUpdateMapRenderer(Object mapRenderer, MapItemSavedData data, ItemStack item) {
        Class<?> dataClass = data.getClass();
        for (var method : mapRenderer.getClass().getMethods()) {
            String name = method.getName();
            if (!isMapUpdateMethod(name)) {
                continue;
            }
            Class<?>[] params = method.getParameterTypes();
            if (params.length == 1) {
                if (params[0].isAssignableFrom(dataClass)) {
                    if (invokeMapUpdate(mapRenderer, method, data)) {
                        VoxelBridgeLogger.info(LogModule.DYNAMIC_MAP, "[DynamicMap] MapRenderer updated: " + name + "(data)");
                        return true;
                    }
                }
                continue;
            }
            if (params.length != 2) {
                continue;
            }
            if (params[0].isAssignableFrom(dataClass) && params[1].isAssignableFrom(ItemStack.class)) {
                if (invokeMapUpdate(mapRenderer, method, data, item)) {
                    VoxelBridgeLogger.info(LogModule.DYNAMIC_MAP, "[DynamicMap] MapRenderer updated: " + name + "(data,item)");
                    return true;
                }
                continue;
            }
            if (params[1].isAssignableFrom(dataClass) && params[0].isAssignableFrom(ItemStack.class)) {
                if (invokeMapUpdate(mapRenderer, method, item, data)) {
                    VoxelBridgeLogger.info(LogModule.DYNAMIC_MAP, "[DynamicMap] MapRenderer updated: " + name + "(item,data)");
                    return true;
                }
                continue;
            }
            if (params[1].isAssignableFrom(dataClass)) {
                Object idArg = resolveMapIdArg(params[0], data, item);
                if (idArg != null && invokeMapUpdate(mapRenderer, method, idArg, data)) {
                    VoxelBridgeLogger.info(LogModule.DYNAMIC_MAP, "[DynamicMap] MapRenderer updated: " + name + "(" + idArg + ",data)");
                    return true;
                }
            }
            if (params[0].isAssignableFrom(dataClass)) {
                Object idArg = resolveMapIdArg(params[1], data, item);
                if (idArg != null && invokeMapUpdate(mapRenderer, method, data, idArg)) {
                    VoxelBridgeLogger.info(LogModule.DYNAMIC_MAP, "[DynamicMap] MapRenderer updated: " + name + "(data," + idArg + ")");
                    return true;
                }
            }
        }
        logMapRendererMethods(mapRenderer);
        return false;
    }

    private static boolean isMapUpdateMethod(String name) {
        if (name == null) {
            return false;
        }
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        return lower.equals("update")
            || lower.contains("update")
            || lower.contains("refresh")
            || lower.contains("render");
    }

    private static boolean invokeMapUpdate(Object mapRenderer, java.lang.reflect.Method method, Object... args) {
        try {
            method.invoke(mapRenderer, args);
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private static void logMapRendererMethods(Object mapRenderer) {
        if (mapRenderer == null) {
            return;
        }
        String key = mapRenderer.getClass().getName();
        if (!LOGGED_MAP_RENDERERS.add(key)) {
            return;
        }
        StringBuilder sb = new StringBuilder("[DynamicMap] MapRenderer methods:");
        for (var method : mapRenderer.getClass().getMethods()) {
            sb.append(" ").append(method.getName()).append("(");
            Class<?>[] params = method.getParameterTypes();
            for (int i = 0; i < params.length; i++) {
                if (i > 0) sb.append(",");
                sb.append(params[i].getSimpleName());
            }
            sb.append(")");
        }
        VoxelBridgeLogger.info(LogModule.DYNAMIC_MAP, sb.toString());
    }

    private static Object resolveMapIdArg(Class<?> expectedType, MapItemSavedData data, ItemStack item) {
        if (expectedType == int.class || expectedType == Integer.class) {
            Integer id = extractMapIdInt(data, item);
            return id != null ? id : null;
        }
        Object idFromStack = extractMapIdFromStack(expectedType, item);
        if (idFromStack != null) {
            return idFromStack;
        }
        Object idFromData = extractMapIdFromData(expectedType, data);
        if (idFromData != null) {
            return idFromData;
        }
        if (isMapIdType(expectedType)) {
            Integer rawId = extractMapIdInt(data, item);
            if (rawId != null) {
                return createMapId(expectedType, rawId);
            }
        }
        return null;
    }

    private static Integer extractMapIdInt(MapItemSavedData data, ItemStack item) {
        Integer fromData = extractIntFromData(data);
        if (fromData != null) {
            return fromData;
        }
        Object fromStack = extractMapIdFromStack(Integer.class, item);
        if (fromStack instanceof Integer id) {
            return id;
        }
        return null;
    }

    private static Integer extractIntFromData(MapItemSavedData data) {
        for (var method : data.getClass().getMethods()) {
            if (method.getParameterCount() != 0) {
                continue;
            }
            if (method.getReturnType() != int.class && method.getReturnType() != Integer.class) {
                continue;
            }
            String name = method.getName().toLowerCase(java.util.Locale.ROOT);
            if (!name.contains("id")) {
                continue;
            }
            try {
                Object value = method.invoke(data);
                if (value instanceof Integer id) {
                    return id;
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }
        Class<?> type = data.getClass();
        while (type != null && type != Object.class) {
            try {
                var field = type.getDeclaredField("id");
                field.setAccessible(true);
                Object value = field.get(data);
                if (value instanceof Integer id) {
                    return id;
                }
            } catch (ReflectiveOperationException ignored) {
            }
            type = type.getSuperclass();
        }
        return null;
    }

    private static Object extractMapIdFromStack(Class<?> expectedType, ItemStack item) {
        try {
            var method = MapItem.class.getMethod("getMapId", ItemStack.class);
            Object value = method.invoke(null, item);
            if (expectedType.isInstance(value)) {
                return value;
            }
            if (value instanceof Integer id && isMapIdType(expectedType)) {
                return createMapId(expectedType, id);
            }
            return null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Object extractMapIdFromData(Class<?> expectedType, MapItemSavedData data) {
        for (var method : data.getClass().getMethods()) {
            if (method.getParameterCount() != 0) {
                continue;
            }
            if (!expectedType.isAssignableFrom(method.getReturnType())) {
                continue;
            }
            String name = method.getName().toLowerCase(java.util.Locale.ROOT);
            if (!name.contains("id")) {
                continue;
            }
            try {
                Object value = method.invoke(data);
                return expectedType.isInstance(value) ? value : null;
            } catch (ReflectiveOperationException ignored) {
            }
        }
        Class<?> type = data.getClass();
        while (type != null && type != Object.class) {
            for (var field : type.getDeclaredFields()) {
                if (!expectedType.isAssignableFrom(field.getType())) {
                    continue;
                }
                String name = field.getName().toLowerCase(java.util.Locale.ROOT);
                if (!name.contains("id")) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    Object value = field.get(data);
                    return expectedType.isInstance(value) ? value : null;
                } catch (ReflectiveOperationException ignored) {
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }

    private static boolean isMapIdType(Class<?> type) {
        if (type == null) {
            return false;
        }
        String name = type.getSimpleName();
        return "MapId".equals(name) || name.endsWith(".MapId");
    }

    private static Object createMapId(Class<?> mapIdType, int id) {
        if (mapIdType == null) {
            return null;
        }
        for (String name : new String[] {"of", "fromId", "valueOf"}) {
            try {
                var method = mapIdType.getMethod(name, int.class);
                Object value = method.invoke(null, id);
                if (mapIdType.isInstance(value)) {
                    return value;
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }
        try {
            var ctor = mapIdType.getDeclaredConstructor(int.class);
            ctor.setAccessible(true);
            Object value = ctor.newInstance(id);
            if (mapIdType.isInstance(value)) {
                return value;
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return null;
    }

    /**
     * Capture buffer for entity renders.
     */
    private static class CaptureBuffer extends CaptureBufferBase {
        private final double offsetX, offsetY, offsetZ;
        private final Entity entity;
        private int quadCount = 0;
        private float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
        private float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;

        CaptureBuffer(ExportContext ctx, IrSink sceneSink, double offsetX, double offsetY, double offsetZ, Entity entity) {
            super(ctx, sceneSink, null);
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.offsetZ = offsetZ;
            this.entity = entity;
        }

        void flush() {
            flushCapture();
            if (hadGeometry()) {
                if (VoxelBridgeLogger.isDebugEnabled(LogModule.ENTITY)) {
                    VoxelBridgeLogger.debug(LogModule.ENTITY, String.format(
                        "[Geometry] %s vertices=%d quads=%d",
                        entity.getType(), quadCount * 4, quadCount));
                }
                VoxelBridgeLogger.debug(LogModule.ENTITY, String.format(
                    "[Bounds] min[%.4f, %.4f, %.4f] max[%.4f, %.4f, %.4f]",
                    minX, minY, minZ, maxX, maxY, maxZ));
            }
        }

        @Override
        public void onQuad(RenderType renderType, List<RenderCapture.Vertex> verts) {
            if (verts.size() < 3) return;

            recordGeometry();
            quadCount++;

            float[] positions = new float[12];
            float[] uv0 = new float[8];
            float[] colors = new float[16];

            for (int i = 0; i < Math.min(4, verts.size()); i++) {
                RenderCapture.Vertex v = verts.get(i);
                positions[i * 3] = v.x;
                positions[i * 3 + 1] = v.y;
                positions[i * 3 + 2] = v.z;

                minX = Math.min(minX, v.x);
                minY = Math.min(minY, v.y);
                minZ = Math.min(minZ, v.z);
                maxX = Math.max(maxX, v.x);
                maxY = Math.max(maxY, v.y);
                maxZ = Math.max(maxZ, v.z);

                colors[i * 4] = ((v.color >> 16) & 0xFF) / 255.0f;
                colors[i * 4 + 1] = ((v.color >> 8) & 0xFF) / 255.0f;
                colors[i * 4 + 2] = (v.color & 0xFF) / 255.0f;
                colors[i * 4 + 3] = ((v.color >> 24) & 0xFF) / 255.0f;
            }

            if (VoxelBridgeLogger.isDebugEnabled(LogModule.ENTITY)) {
                VoxelBridgeLogger.debug(LogModule.ENTITY, String.format(
                    "[Quad#%d] Vertices: v0=[%.3f,%.3f,%.3f] v1=[%.3f,%.3f,%.3f]",
                    quadCount,
                    positions[0], positions[1], positions[2],
                    positions[3], positions[4], positions[5]));
            }

            if (VoxelBridgeLogger.isTraceEnabled(LogModule.ENTITY)) {
                VoxelBridgeLogger.trace(LogModule.ENTITY, String.format(
                    "[RenderType] %s renderType=%s",
                    entity.getType(),
                    renderType != null ? renderType.toString() : "null"));
            }
            ResolvedTexture textureRes = TEXTURE_RESOLVER.resolve(entity, renderType);
            String spriteKey;
            boolean isAtlasTexture = false;
            float u0 = 0f, u1 = 1f, v0 = 0f, v1 = 1f;
            ResourceLocation atlasLocation = textureRes != null ? textureRes.atlasLocation() : null;

            RenderCaptureUtil.UvStats uvStats = RenderCaptureUtil.computeUvStats(verts);

            if (textureRes != null && textureRes.isAtlasTexture() && textureRes.sprite() == null) {
                textureRes = RenderCaptureUtil.resolveAtlasSprite(textureRes, ATLAS_LOCATOR, uvStats, atlasLocation);
                if (textureRes != null) {
                    atlasLocation = textureRes.atlasLocation();
                }
            }

            if (entity instanceof net.minecraft.world.entity.decoration.Painting painting
                && textureRes != null && textureRes.isAtlasTexture() && textureRes.sprite() != null) {
                textureRes = selectPaintingTexture(painting, textureRes, positions);
                atlasLocation = textureRes.atlasLocation();
            }

            String materialGroupKey = "entity:" + net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());

            if (textureRes != null && textureRes.texture() != null) {
                EntityTextureManager.TextureHandle handle = null;
                if (entity instanceof AbstractClientPlayer player) {
                    handle = tryRegisterPlayerAttachmentTexture(ctx, player, textureRes.texture());
                }
                if (handle == null) {
                    handle = EntityTextureManager.register(ctx, textureRes.texture().toString());
                }
                spriteKey = handle.spriteKey();
                isAtlasTexture = textureRes.isAtlasTexture();
                u0 = textureRes.u0(); u1 = textureRes.u1();
                v0 = textureRes.v0(); v1 = textureRes.v1();

                if (VoxelBridgeLogger.isDebugEnabled(LogModule.ENTITY)) {
                    VoxelBridgeLogger.debug(LogModule.ENTITY, String.format(
                        "[Texture] %s texture=%s isAtlas=%s",
                        entity.getType(),
                        textureRes.texture() != null ? textureRes.texture() : "null",
                        isAtlasTexture));
                }
                if (VoxelBridgeLogger.isTraceEnabled(LogModule.ENTITY)) {
                    VoxelBridgeLogger.trace(LogModule.ENTITY, String.format(
                        "[UV] %s u=[%.4f, %.4f] v=[%.4f, %.4f]",
                        entity.getType(), u0, u1, v0, v1));
                }

                // Cache atlas sprite pixels for export.
                if (isAtlasTexture && textureRes.sprite() != null) {
                    BufferedImage spriteImg = ctx.getTextureAccess().readSprite(textureRes.sprite());
                    if (spriteImg != null) {
                        ctx.cacheSpriteImage(spriteKey, spriteImg);
                    }
                }
            } else {
                spriteKey = "entity:minecraft/white";
                VoxelBridgeLogger.debug(LogModule.ENTITY, "[Quad#" + quadCount + "] No texture resolved, using white fallback");
            }

            if (isAtlasTexture && textureRes != null && textureRes.sprite() != null) {
                float eps = 1e-4f;
                boolean outsideSpriteBounds =
                    uvStats.minU() < textureRes.u0() - eps || uvStats.maxU() > textureRes.u1() + eps ||
                    uvStats.minV() < textureRes.v0() - eps || uvStats.maxV() > textureRes.v1() + eps;
                if (outsideSpriteBounds) {
                    isAtlasTexture = false;
                    u0 = 0f; u1 = 1f;
                    v0 = 0f; v1 = 1f;
                }
            }

            fillUvs(verts, uv0, isAtlasTexture, u0, u1, v0, v1);

            String resolvedMaterialKey = ctx.resolveMaterialKey(spriteKey, materialGroupKey);
            ctx.registerSpriteMaterial(spriteKey, resolvedMaterialKey);
            RenderCaptureUtil.ColorModeResult colorResult =
                RenderCaptureUtil.applyColorMode(ctx, colors, EMPTY_UV);
            sceneSink.addQuad(resolvedMaterialKey, spriteKey, "voxelbridge:transparent",
                RenderLayer.UNKNOWN, colorResult.tintMode(),
                RENDER_TYPE_RESOLVER.isDoubleSided(renderType),
                false,
                positions, uv0, colorResult.uv1(), NORMAL_UP, colors);
        }

        private void fillUvs(List<RenderCapture.Vertex> verts, float[] uv0, boolean isAtlas,
                             float u0, float u1, float v0, float v1) {
            int count = Math.min(4, verts.size());
            if (isAtlas) {
                // Atlas texture: normalize UV within sprite bounds
                RenderCaptureUtil.fillUvsAtlas(verts, uv0, u0, u1, v0, v1);
            } else {
                // Non-atlas texture: find actual UV bounds and normalize
                // This is important for entities like paintings where UV may not be in [0,1]
                float minU = Float.POSITIVE_INFINITY, maxU = Float.NEGATIVE_INFINITY;
                float minV = Float.POSITIVE_INFINITY, maxV = Float.NEGATIVE_INFINITY;
                for (int i = 0; i < count; i++) {
                    RenderCapture.Vertex v = verts.get(i);
                    minU = Math.min(minU, v.u);
                    maxU = Math.max(maxU, v.u);
                    minV = Math.min(minV, v.v);
                    maxV = Math.max(maxV, v.v);
                }

                float rangeU = maxU - minU;
                float rangeV = maxV - minV;

                // If UV extends significantly beyond [0,1] OR has zero range, normalize
                boolean needsNormalization =
                    maxU > 1.1f || minU < -0.1f || maxV > 1.1f || minV < -0.1f ||
                    rangeU < 1e-6f || rangeV < 1e-6f;
                if (needsNormalization && rangeU > 1e-6f && rangeV > 1e-6f) {
                    VoxelBridgeLogger.debug(LogModule.ENTITY, String.format(
                        "[UV Normalization] Painting/Entity UV remapped: U[%.3f, %.3f] V[%.3f, %.3f] -> [0,1]x[0,1]",
                        minU, maxU, minV, maxV));
                    for (int i = 0; i < count; i++) {
                        RenderCapture.Vertex v = verts.get(i);
                        float su = (v.u - minU) / rangeU;
                        float sv = (v.v - minV) / rangeV;
                        uv0[i * 2] = Math.max(0f, Math.min(1f, su));
                        uv0[i * 2 + 1] = Math.max(0f, Math.min(1f, sv));
                    }
                } else if (rangeU < 1e-6f || rangeV < 1e-6f) {
                    VoxelBridgeLogger.debug(LogModule.ENTITY, "[UV Normalization] Degenerate UV detected, using [0,0] for all vertices");
                    for (int i = 0; i < count; i++) {
                        uv0[i * 2] = 0f;
                        uv0[i * 2 + 1] = 0f;
                    }
                } else {
                    for (int i = 0; i < count; i++) {
                        RenderCapture.Vertex v = verts.get(i);
                        uv0[i * 2] = Math.max(0f, Math.min(1f, v.u));
                        uv0[i * 2 + 1] = Math.max(0f, Math.min(1f, v.v));
                    }
                }
            }
        }

        private ResolvedTexture selectPaintingTexture(
            net.minecraft.world.entity.decoration.Painting painting,
            ResolvedTexture current,
            float[] positions
        ) {
            ResolvedTexture normalized = normalizePaintingTexture(current);
            if (isPaintingFrontQuad(painting, positions)) {
                return normalized;
            }

            TextureAtlasSprite backSprite = ClientAccessHolder.get().getPaintingTextures().getBackSprite();
            if (backSprite == null || isMissingSprite(backSprite)) {
                return normalized;
            }

            ResourceLocation spriteName = backSprite.contents() != null ? backSprite.contents().name() : normalized.texture();
            spriteName = normalizePaintingSpriteName(spriteName);
            ResourceLocation atlas = backSprite.atlasLocation();
            return new ResolvedTexture(spriteName, backSprite.getU0(), backSprite.getU1(),
                backSprite.getV0(), backSprite.getV1(), true, backSprite, atlas);
        }

        private ResolvedTexture normalizePaintingTexture(ResolvedTexture current) {
            if (current == null || current.texture() == null) {
                return current;
            }
            ResourceLocation normalized = normalizePaintingSpriteName(current.texture());
            if (normalized.equals(current.texture())) {
                return current;
            }
            return new ResolvedTexture(normalized, current.u0(), current.u1(),
                current.v0(), current.v1(), current.isAtlasTexture(), current.sprite(), current.atlasLocation());
        }

        private boolean isPaintingFrontQuad(net.minecraft.world.entity.decoration.Painting painting, float[] positions) {
            Direction direction = painting.getDirection();
            if (direction == null) {
                return true;
            }

            int axisIndex;
            double frontCoord;
            var bounds = painting.getBoundingBox().move(offsetX, offsetY, offsetZ);
            switch (direction) {
                case NORTH -> {
                    axisIndex = 2;
                    frontCoord = bounds.minZ;
                }
                case SOUTH -> {
                    axisIndex = 2;
                    frontCoord = bounds.maxZ;
                }
                case WEST -> {
                    axisIndex = 0;
                    frontCoord = bounds.minX;
                }
                case EAST -> {
                    axisIndex = 0;
                    frontCoord = bounds.maxX;
                }
                default -> {
                    return true;
                }
            }

            float center = 0f;
            for (int i = 0; i < 4; i++) {
                center += positions[i * 3 + axisIndex];
            }
            center /= 4f;
            return Math.abs(center - frontCoord) <= 1e-3f;
        }

        private ResourceLocation normalizePaintingSpriteName(ResourceLocation spriteName) {
            if (spriteName == null) {
                return null;
            }
            String path = spriteName.getPath();
            if (path.startsWith("textures/painting/") || path.startsWith("painting/")) {
                return spriteName;
            }
            return ResourceLocation.fromNamespaceAndPath(spriteName.getNamespace(), "painting/" + path);
        }

        private boolean isMissingSprite(TextureAtlasSprite sprite) {
            return sprite.contents().name().toString().contains("missingno");
        }

    }

    private static EntityTextureManager.TextureHandle tryRegisterPlayerAttachmentTexture(
        ExportContext ctx,
        AbstractClientPlayer player,
        ResourceLocation texture
    ) {
        String type = detectPlayerAttachmentType(texture);
        if (type == null) {
            return null;
        }
        BufferedImage image = readTextureWithFallback(texture);
        if (image == null) {
            return null;
        }
        String playerName = sanitizePlayerName(player.getGameProfile().getName());
        String key = "entity:player/" + type + "/" + playerName;
        String relativePath = "textures/entity_textures/player/" + playerName + "_" + type + ".png";
        return EntityTextureManager.registerGenerated(ctx, key, relativePath, image);
    }

    private static String detectPlayerAttachmentType(ResourceLocation texture) {
        if (texture == null) {
            return null;
        }
        String path = texture.getPath().toLowerCase(Locale.ROOT);
        if (path.contains("elytra")) {
            return "elytra";
        }
        if (path.contains("cape") || path.contains("cloak")) {
            return "cape";
        }
        return null;
    }

    private static BufferedImage readTextureWithFallback(ResourceLocation texture) {
        BufferedImage image = TextureLoader.readTexture(texture, ExportRuntimeConfig.isAnimationEnabled());
        if (image != null) {
            return image;
        }
        ResourceLocation fallback = resolveTexturePathFallback(texture);
        if (!fallback.equals(texture)) {
            return TextureLoader.readTexture(fallback, ExportRuntimeConfig.isAnimationEnabled());
        }
        return null;
    }

    private static ResourceLocation resolveTexturePathFallback(ResourceLocation texture) {
        String path = texture.getPath();
        if (path.startsWith("skins/") || path.startsWith("skin/")) {
            return texture;
        }
        if (!path.startsWith("textures/")) {
            path = "textures/" + path;
        }
        if (!path.endsWith(".png")) {
            path = path + ".png";
        }
        return ResourceLocation.fromNamespaceAndPath(texture.getNamespace(), path);
    }

    private static String sanitizePlayerName(String name) {
        if (name == null || name.isEmpty()) {
            return "player";
        }
        String lower = name.toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z')
                || (c >= '0' && c <= '9')
                || c == '.' || c == '-' || c == '_';
            out.append(ok ? c : '_');
        }
        return out.toString();
    }
}

