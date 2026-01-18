package com.voxelbridge.platform.render;

import com.voxelbridge.export.exporter.resolve.RenderTypeResolver;
import com.voxelbridge.util.debug.LogModule;
import com.voxelbridge.util.debug.VoxelBridgeLogger;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;


/**
 * Resolves texture resource locations from RenderType instances using reflection.
 */
public final class RenderTypeTextureResolver implements RenderTypeResolver {

    public static final RenderTypeTextureResolver INSTANCE = new RenderTypeTextureResolver();

    private RenderTypeTextureResolver() {
    }

    /**
     * Attempts to extract the texture ResourceLocation from a RenderType.
     *
     * @param renderType the render type
     * @return the texture location, or null if it cannot be determined
     */
    @Override
    public ResourceLocation resolve(RenderType renderType) {
        if (renderType == null) {
            return null;
        }

        ResourceLocation fromState = extractFromState(renderType);
        if (fromState != null) {
            logTextRenderType(renderType, fromState);
            return sanitize(fromState);
        }

        try {
            // Try to get the texture from the RenderType
            // This uses reflection since RenderType internals are not part of the public API
            ResourceLocation extracted = extractTextureViaReflection(renderType);
            if (extracted != null) {
                logTextRenderType(renderType, extracted);
                return sanitize(extracted);
            }
            ResourceLocation fromFields = extractFromRenderTypeFields(renderType);
            if (fromFields != null) {
                logTextRenderType(renderType, fromFields);
                return sanitize(fromFields);
            }
        } catch (Exception e) {
            // Log reflection failure for debugging
            VoxelBridgeLogger.warn(LogModule.TEXTURE_RESOLVE, "[RenderTypeTextureResolver] Reflection failed for " +
                renderType + ": " + e.getClass().getSimpleName() + " - " + e.getMessage());
        }

        logTextRenderType(renderType, null);
        return null;
    }

    /**
     * Determines if the RenderType disables back-face culling.
     */
    @Override
    public boolean isDoubleSided(RenderType renderType) {
        try {
            RenderType.CompositeState state = compositeState(renderType);
            if (state == null) {
                return false;
            }
            Field cullField = RenderType.CompositeState.class.getDeclaredField("cullState");
            cullField.setAccessible(true);
            Object cullState = cullField.get(state);
            if (cullState == null) {
                return false;
            }
            Class<?> booleanShard = cullState.getClass().getSuperclass(); // BooleanStateShard
            Field enabled = booleanShard.getDeclaredField("enabled");
            enabled.setAccessible(true);
            boolean cullEnabled = enabled.getBoolean(cullState);
            return !cullEnabled;
        } catch (Exception e) {
            return false;
        }
    }

    private static ResourceLocation extractFromState(RenderType renderType) {
        try {
            RenderType.CompositeState state = compositeState(renderType);
            if (state == null) {
                return null;
            }

            Field textureField = RenderType.CompositeState.class.getDeclaredField("textureState");
            textureField.setAccessible(true);
            Object textureState = textureField.get(state);
            if (textureState == null) {
                return null;
            }

            Method cutoutMethod = textureState.getClass().getDeclaredMethod("cutoutTexture");
            cutoutMethod.setAccessible(true);
            Object result = cutoutMethod.invoke(textureState);
            if (result instanceof Optional<?> opt && opt.isPresent() && opt.get() instanceof ResourceLocation loc) {
                return loc;
            }
            return extractFromTextureState(textureState);
        } catch (Exception ignored) {
        }
        return null;
    }

    private static ResourceLocation extractFromTextureState(Object textureState) {
        if (textureState == null) {
            return null;
        }
        for (String methodName : new String[] {"texture", "textureLocation", "getTexture", "getTextureLocation", "location", "getLocation"}) {
            try {
                Method method = textureState.getClass().getDeclaredMethod(methodName);
                method.setAccessible(true);
                Object value = method.invoke(textureState);
                ResourceLocation loc = unwrapLocation(value);
                if (loc != null) {
                    return loc;
                }
            } catch (Exception ignored) {
            }
        }
        for (String fieldName : new String[] {"texture", "location", "resourceLocation", "loc"}) {
            try {
                Field field = textureState.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                Object value = field.get(textureState);
                ResourceLocation loc = unwrapLocation(value);
                if (loc != null) {
                    return loc;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static ResourceLocation unwrapLocation(Object value) {
        if (value instanceof Optional<?> opt) {
            value = opt.orElse(null);
        }
        return value instanceof ResourceLocation loc ? loc : null;
    }

    private static RenderType.CompositeState compositeState(RenderType renderType) {
        try {
            // Use reflection to access CompositeRenderType and state() method
            Class<?> compositeRenderTypeClass = Class.forName("net.minecraft.client.renderer.RenderType$CompositeRenderType");
            if (!compositeRenderTypeClass.isInstance(renderType)) {
                return null;
            }

            Method stateMethod = compositeRenderTypeClass.getDeclaredMethod("state");
            stateMethod.setAccessible(true);
            Object state = stateMethod.invoke(renderType);
            if (state instanceof RenderType.CompositeState compositeState) {
                return compositeState;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static ResourceLocation extractTextureViaReflection(RenderType renderType) {
        try {
            // Try to access toString() and parse it (fallback method)
            String name = renderType.toString();
            if (name.contains("RenderType[")) {
                // Parse texture from toString output if possible
                // Format is usually like "RenderType[name, texture=minecraft:textures/..., ...]"
                int texIdx = name.indexOf("texture=");
                if (texIdx >= 0) {
                    int start = texIdx + 8;
                    int end = name.indexOf(",", start);
                    if (end < 0) end = name.indexOf("]", start);
                    if (end > start) {
                        String texStr = name.substring(start, end).trim();
                        return ResourceLocation.parse(texStr);
                    }
                }
            }
            String optional = parseOptionalTexture(name);
            if (optional != null) {
                return ResourceLocation.parse(optional);
            }
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }

    private static ResourceLocation extractFromRenderTypeFields(RenderType renderType) {
        if (renderType == null) {
            return null;
        }
        java.util.IdentityHashMap<Object, Boolean> seen = new java.util.IdentityHashMap<>();
        return scanForLocation(renderType, 2, seen);
    }

    private static ResourceLocation scanForLocation(Object value, int depth,
                                                    java.util.IdentityHashMap<Object, Boolean> seen) {
        if (value == null || depth < 0) {
            return null;
        }
        if (value instanceof ResourceLocation loc) {
            return loc;
        }
        if (value instanceof Optional<?> opt) {
            Object inner = opt.orElse(null);
            if (inner instanceof ResourceLocation loc) {
                return loc;
            }
        }
        String typeName = value.getClass().getName();
        if (typeName.startsWith("java.") || typeName.startsWith("javax.")
            || typeName.startsWith("sun.") || typeName.startsWith("jdk.")) {
            return null;
        }
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                ResourceLocation loc = scanForLocation(item, depth - 1, seen);
                if (loc != null) {
                    return loc;
                }
            }
        }
        Class<?> type = value.getClass();
        if (type.isArray()) {
            int len = java.lang.reflect.Array.getLength(value);
            for (int i = 0; i < len; i++) {
                Object item = java.lang.reflect.Array.get(value, i);
                ResourceLocation loc = scanForLocation(item, depth - 1, seen);
                if (loc != null) {
                    return loc;
                }
            }
            return null;
        }
        if (seen.put(value, Boolean.TRUE) != null) {
            return null;
        }
        while (type != null && type != Object.class) {
            Field[] fields = type.getDeclaredFields();
            for (Field field : fields) {
                try {
                    field.setAccessible(true);
                    Object fieldValue = field.get(value);
                    ResourceLocation loc = scanForLocation(fieldValue, depth - 1, seen);
                    if (loc != null) {
                        return loc;
                    }
                } catch (IllegalAccessException ignored) {
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }

    private static String parseOptionalTexture(String renderTypeString) {
        if (renderTypeString == null) {
            return null;
        }
        String marker = "texture[Optional[";
        int start = renderTypeString.indexOf(marker);
        if (start < 0) {
            return null;
        }
        int valueStart = start + marker.length();
        int valueEnd = renderTypeString.indexOf("]", valueStart);
        if (valueEnd <= valueStart) {
            return null;
        }
        String tex = renderTypeString.substring(valueStart, valueEnd).trim();
        if (tex.isEmpty() || "empty".equals(tex)) {
            return null;
        }
        return tex;
    }

    private static ResourceLocation sanitize(ResourceLocation loc) {
        if (loc == null) return null;
        String namespace = loc.getNamespace();
        String path = loc.getPath();
        if (namespace.contains(":")) {
            namespace = namespace.replace(':', '_');
        }
        if (path.contains(":")) {
            path = path.replace(':', '/');
        }
        if (namespace.equals(loc.getNamespace()) && path.equals(loc.getPath())) {
            return loc;
        }
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }

    private static void logTextRenderType(RenderType renderType, ResourceLocation loc) {
        if (renderType == null) {
            return;
        }
        String name = renderType.toString().toLowerCase(java.util.Locale.ROOT);
        boolean isText = name.contains("text_")
            || name.contains("neoforge_text")
            || name.contains("font")
            || name.contains("glyph");
        if (!isText) {
            return;
        }
        VoxelBridgeLogger.info(LogModule.TEXTURE_RESOLVE,
            "[RenderTypeTextureResolver] text renderType=" + renderType + " resolved=" + loc);
    }
}
