package com.voxelbridge.compat;

import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.lang.reflect.Method;

/**
 * Version-agnostic helpers for RenderLevelStageEvent API drift.
 */
public final class RenderStageCompat {

    private static final Object PREFERRED_STAGE = resolvePreferredStage();

    private RenderStageCompat() {}

    public static boolean isAfterTranslucent(RenderLevelStageEvent event) {
        if (event == null) {
            return false;
        }
        try {
            Method method = event.getClass().getMethod("getStage");
            Object stage = method.invoke(event);
            if (stage == null) {
                return true;
            }
            String name = stageName(stage);
            if (PREFERRED_STAGE != null) {
                if (stage == PREFERRED_STAGE || PREFERRED_STAGE.equals(stage)) {
                    return true;
                }
            }
            if (name == null) {
                return true;
            }
            String upper = name.toUpperCase(java.util.Locale.ROOT);
            return upper.contains("AFTER_TRANSLUCENT");
        } catch (ReflectiveOperationException ignored) {
            return true;
        }
    }

    private static Object resolvePreferredStage() {
        try {
            Class<?> stageClass = Class.forName("net.neoforged.neoforge.client.event.RenderLevelStageEvent$Stage");
            Object stage = getField(stageClass, "AFTER_LEVEL");
            if (stage != null) {
                return stage;
            }
            stage = getField(stageClass, "AFTER_TRANSLUCENT_BLOCKS");
            if (stage != null) {
                return stage;
            }
            stage = getField(stageClass, "AFTER_TRANSLUCENT");
            if (stage != null) {
                return stage;
            }
        } catch (Throwable ignored) {
            // Fall through to default.
        }
        return null;
    }

    private static Object getField(Class<?> type, String name) {
        try {
            return type.getField(name).get(null);
        } catch (NoSuchFieldException ignored) {
            return null;
        } catch (IllegalAccessException ignored) {
            return null;
        }
    }

    private static String stageName(Object stage) {
        if (stage instanceof Enum<?> en) {
            return en.name();
        }
        try {
            Method nameMethod = stage.getClass().getMethod("name");
            Object value = nameMethod.invoke(stage);
            if (value instanceof String s) {
                return s;
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return stage.toString();
    }
}
