package com.voxelbridge.adapter;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;

/**
 * NeoForge implementation of PlatformRenderHelper.
 */
public class NeoForgePlatformRenderHelper implements PlatformRenderHelper {

    // RenderType helpers
    @Override
    public ResourceLocation getRenderTypeTexture(net.minecraft.client.renderer.RenderType renderType) {
        if (renderType == null)
            return null;

        ResourceLocation fromState = extractFromState(renderType);
        if (fromState != null) {
            return sanitize(fromState);
        }

        // Try reflection fallback
        ResourceLocation extracted = extractTextureViaReflection(renderType);
        if (extracted != null)
            return sanitize(extracted);

        ResourceLocation fromFields = extractFromRenderTypeFields(renderType);
        if (fromFields != null)
            return sanitize(fromFields);

        return null;
    }

    @Override
    public boolean isRenderTypeDoubleSided(net.minecraft.client.renderer.RenderType renderType) {
        try {
            net.minecraft.client.renderer.RenderType.CompositeState state = compositeState(renderType);
            if (state == null)
                return false;

            java.lang.reflect.Field cullField = net.minecraft.client.renderer.RenderType.CompositeState.class
                    .getDeclaredField("cullState");
            cullField.setAccessible(true);
            Object cullState = cullField.get(state);
            if (cullState == null)
                return false;

            Class<?> booleanShard = cullState.getClass().getSuperclass();
            java.lang.reflect.Field enabled = booleanShard.getDeclaredField("enabled");
            enabled.setAccessible(true);
            return !enabled.getBoolean(cullState);
        } catch (Exception e) {
            return false;
        }
    }

    // --- Private helpers moved from RenderTypeTextureResolver ---

    private static ResourceLocation extractFromState(net.minecraft.client.renderer.RenderType renderType) {
        try {
            net.minecraft.client.renderer.RenderType.CompositeState state = compositeState(renderType);
            if (state == null)
                return null;

            java.lang.reflect.Field textureField = net.minecraft.client.renderer.RenderType.CompositeState.class
                    .getDeclaredField("textureState");
            textureField.setAccessible(true);
            Object textureState = textureField.get(state);
            if (textureState == null)
                return null;

            java.lang.reflect.Method cutoutMethod = textureState.getClass().getDeclaredMethod("cutoutTexture");
            cutoutMethod.setAccessible(true);
            Object result = cutoutMethod.invoke(textureState);
            if (result instanceof java.util.Optional<?> opt && opt.isPresent()
                    && opt.get() instanceof ResourceLocation loc) {
                return loc;
            }
            return extractFromTextureState(textureState);
        } catch (Exception ignored) {
        }
        return null;
    }

    private static ResourceLocation extractFromTextureState(Object textureState) {
        if (textureState == null)
            return null;
        // Try various obfuscated names/accessors
        for (String methodName : new String[] { "texture", "textureLocation", "getTexture", "getTextureLocation",
                "location", "getLocation" }) {
            try {
                java.lang.reflect.Method method = textureState.getClass().getDeclaredMethod(methodName);
                method.setAccessible(true);
                Object value = method.invoke(textureState);
                ResourceLocation loc = unwrapLocation(value);
                if (loc != null)
                    return loc;
            } catch (Exception ignored) {
            }
        }
        for (String fieldName : new String[] { "texture", "location", "resourceLocation", "loc" }) {
            try {
                java.lang.reflect.Field field = textureState.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                Object value = field.get(textureState);
                ResourceLocation loc = unwrapLocation(value);
                if (loc != null)
                    return loc;
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static ResourceLocation unwrapLocation(Object value) {
        if (value instanceof java.util.Optional<?> opt) {
            value = opt.orElse(null);
        }
        return value instanceof ResourceLocation loc ? loc : null;
    }

    private static net.minecraft.client.renderer.RenderType.CompositeState compositeState(
            net.minecraft.client.renderer.RenderType renderType) {
        try {
            Class<?> compositeRenderTypeClass = Class
                    .forName("net.minecraft.client.renderer.RenderType$CompositeRenderType");
            if (!compositeRenderTypeClass.isInstance(renderType))
                return null;

            java.lang.reflect.Method stateMethod = compositeRenderTypeClass.getDeclaredMethod("state");
            stateMethod.setAccessible(true);
            Object state = stateMethod.invoke(renderType);
            if (state instanceof net.minecraft.client.renderer.RenderType.CompositeState compositeState) {
                return compositeState;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static ResourceLocation extractTextureViaReflection(net.minecraft.client.renderer.RenderType renderType) {
        try {
            String name = renderType.toString();
            if (name.contains("RenderType[")) {
                int texIdx = name.indexOf("texture=");
                if (texIdx >= 0) {
                    int start = texIdx + 8;
                    int end = name.indexOf(",", start);
                    if (end < 0)
                        end = name.indexOf("]", start);
                    if (end > start) {
                        String texStr = name.substring(start, end).trim();
                        return ResourceLocation.parse(texStr);
                    }
                }
            }
            String optional = parseOptionalTexture(name);
            if (optional != null)
                return ResourceLocation.parse(optional);
        } catch (Exception e) {
        }
        return null;
    }

    private static ResourceLocation extractFromRenderTypeFields(net.minecraft.client.renderer.RenderType renderType) {
        if (renderType == null)
            return null;
        java.util.IdentityHashMap<Object, Boolean> seen = new java.util.IdentityHashMap<>();
        return scanForLocation(renderType, 2, seen);
    }

    private static ResourceLocation scanForLocation(Object value, int depth,
            java.util.IdentityHashMap<Object, Boolean> seen) {
        if (value == null || depth < 0)
            return null;
        if (value instanceof ResourceLocation loc)
            return loc;
        if (value instanceof java.util.Optional<?> opt) {
            Object inner = opt.orElse(null);
            if (inner instanceof ResourceLocation loc)
                return loc;
        }
        String typeName = value.getClass().getName();
        if (typeName.startsWith("java.") || typeName.startsWith("javax.") || typeName.startsWith("sun.")
                || typeName.startsWith("jdk."))
            return null;

        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                ResourceLocation loc = scanForLocation(item, depth - 1, seen);
                if (loc != null)
                    return loc;
            }
        }

        Class<?> type = value.getClass();
        if (type.isArray()) {
            int len = java.lang.reflect.Array.getLength(value);
            for (int i = 0; i < len; i++) {
                Object item = java.lang.reflect.Array.get(value, i);
                ResourceLocation loc = scanForLocation(item, depth - 1, seen);
                if (loc != null)
                    return loc;
            }
            return null;
        }
        if (seen.put(value, Boolean.TRUE) != null)
            return null;

        while (type != null && type != Object.class) {
            java.lang.reflect.Field[] fields = type.getDeclaredFields();
            for (java.lang.reflect.Field field : fields) {
                try {
                    field.setAccessible(true);
                    Object fieldValue = field.get(value);
                    ResourceLocation loc = scanForLocation(fieldValue, depth - 1, seen);
                    if (loc != null)
                        return loc;
                } catch (IllegalAccessException ignored) {
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }

    private static String parseOptionalTexture(String renderTypeString) {
        if (renderTypeString == null)
            return null;
        String marker = "texture[Optional[";
        int start = renderTypeString.indexOf(marker);
        if (start < 0)
            return null;
        int valueStart = start + marker.length();
        int valueEnd = renderTypeString.indexOf("]", valueStart);
        if (valueEnd <= valueStart)
            return null;
        String tex = renderTypeString.substring(valueStart, valueEnd).trim();
        if (tex.isEmpty() || "empty".equals(tex))
            return null;
        return tex;
    }

    private static ResourceLocation sanitize(ResourceLocation loc) {
        if (loc == null)
            return null;
        String namespace = loc.getNamespace();
        String path = loc.getPath();
        if (namespace.contains(":"))
            namespace = namespace.replace(':', '_');
        if (path.contains(":"))
            path = path.replace(':', '/');
        if (namespace.equals(loc.getNamespace()) && path.equals(loc.getPath()))
            return loc;
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }

    @Override
    public boolean isOnRenderThread() {
        return com.mojang.blaze3d.systems.RenderSystem.isOnRenderThread();
    }

    @Override
    public void recordRenderCall(Runnable task) {
        com.mojang.blaze3d.systems.RenderSystem.recordRenderCall(task::run);
    }

    @Override
    public net.minecraft.world.phys.Vec3 getBlockOffset(net.minecraft.world.level.block.state.BlockState state,
            net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos) {
        if (state == null || pos == null)
            return net.minecraft.world.phys.Vec3.ZERO;
        return state.getOffset(level, pos);
    }

    @Override
    public boolean isSolidRender(net.minecraft.world.level.block.state.BlockState state,
            net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos) {
        if (state == null)
            return false;
        return state.isSolidRender(level, pos);
    }

    @Override
    public com.mojang.blaze3d.vertex.PoseStack getGuiPose(net.minecraft.client.gui.GuiGraphics gfx) {
        if (gfx == null)
            return null;
        return gfx.pose();
    }

    @Override
    public void pushPose(com.mojang.blaze3d.vertex.PoseStack pose) {
        if (pose != null)
            pose.pushPose();
    }

    @Override
    public void popPose(com.mojang.blaze3d.vertex.PoseStack pose) {
        if (pose != null)
            pose.popPose();
    }

    @Override
    public void translatePose(com.mojang.blaze3d.vertex.PoseStack pose, float x, float y, float z) {
        if (pose != null)
            pose.translate(x, y, z);
    }
}
