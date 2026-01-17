package com.voxelbridge.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.phys.AABB;

import java.lang.reflect.Method;

/**
 * Version-agnostic helpers for LevelRenderer API drift.
 */
public final class LevelRendererCompat {

    private enum Mode {
        POSESTACK_AABB,
        POSESTACK_POSE_AABB,
        POSESTACK_COORDS,
        NONE
    }

    private static final Method METHOD;
    private static final Mode MODE;

    static {
        Method found = null;
        Mode mode = Mode.NONE;
        for (Method method : LevelRenderer.class.getMethods()) {
            if (!"renderLineBox".equals(method.getName())) {
                continue;
            }
            Class<?>[] params = method.getParameterTypes();
            if (params.length == 7
                && PoseStack.class.isAssignableFrom(params[0])
                && VertexConsumer.class.isAssignableFrom(params[1])
                && AABB.class.isAssignableFrom(params[2])) {
                found = method;
                mode = Mode.POSESTACK_AABB;
                break;
            }
            if (params.length == 7
                && params[0].getSimpleName().equals("Pose")
                && VertexConsumer.class.isAssignableFrom(params[1])
                && AABB.class.isAssignableFrom(params[2])) {
                found = method;
                mode = Mode.POSESTACK_POSE_AABB;
                break;
            }
            if (params.length == 11
                && PoseStack.class.isAssignableFrom(params[0])
                && VertexConsumer.class.isAssignableFrom(params[1])) {
                found = method;
                mode = Mode.POSESTACK_COORDS;
                break;
            }
        }
        METHOD = found;
        MODE = mode;
    }

    private LevelRendererCompat() {}

    public static void renderLineBox(PoseStack poseStack, VertexConsumer consumer, AABB box,
                                     float r, float g, float b, float a) {
        if (METHOD == null || MODE == Mode.NONE || poseStack == null || consumer == null || box == null) {
            renderFallback(poseStack, consumer, box, r, g, b, a);
            return;
        }
        try {
            switch (MODE) {
                case POSESTACK_AABB -> METHOD.invoke(null, poseStack, consumer, box, r, g, b, a);
                case POSESTACK_POSE_AABB -> METHOD.invoke(null, poseStack.last(), consumer, box, r, g, b, a);
                case POSESTACK_COORDS -> METHOD.invoke(null, poseStack, consumer,
                    box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, r, g, b, a);
                default -> { }
            }
        } catch (ReflectiveOperationException ignored) {
            renderFallback(poseStack, consumer, box, r, g, b, a);
        }
    }

    private static void renderFallback(PoseStack poseStack, VertexConsumer consumer, AABB box,
                                       float r, float g, float b, float a) {
        if (poseStack == null || consumer == null || box == null) {
            return;
        }
        PoseStack.Pose pose = poseStack.last();

        double minX = box.minX;
        double minY = box.minY;
        double minZ = box.minZ;
        double maxX = box.maxX;
        double maxY = box.maxY;
        double maxZ = box.maxZ;

        // X edges
        emitLine(pose, consumer, minX, minY, minZ, maxX, minY, minZ, r, g, b, a, 1f, 0f, 0f);
        emitLine(pose, consumer, minX, maxY, minZ, maxX, maxY, minZ, r, g, b, a, 1f, 0f, 0f);
        emitLine(pose, consumer, minX, minY, maxZ, maxX, minY, maxZ, r, g, b, a, 1f, 0f, 0f);
        emitLine(pose, consumer, minX, maxY, maxZ, maxX, maxY, maxZ, r, g, b, a, 1f, 0f, 0f);

        // Y edges
        emitLine(pose, consumer, minX, minY, minZ, minX, maxY, minZ, r, g, b, a, 0f, 1f, 0f);
        emitLine(pose, consumer, maxX, minY, minZ, maxX, maxY, minZ, r, g, b, a, 0f, 1f, 0f);
        emitLine(pose, consumer, minX, minY, maxZ, minX, maxY, maxZ, r, g, b, a, 0f, 1f, 0f);
        emitLine(pose, consumer, maxX, minY, maxZ, maxX, maxY, maxZ, r, g, b, a, 0f, 1f, 0f);

        // Z edges
        emitLine(pose, consumer, minX, minY, minZ, minX, minY, maxZ, r, g, b, a, 0f, 0f, 1f);
        emitLine(pose, consumer, maxX, minY, minZ, maxX, minY, maxZ, r, g, b, a, 0f, 0f, 1f);
        emitLine(pose, consumer, minX, maxY, minZ, minX, maxY, maxZ, r, g, b, a, 0f, 0f, 1f);
        emitLine(pose, consumer, maxX, maxY, minZ, maxX, maxY, maxZ, r, g, b, a, 0f, 0f, 1f);
    }

    private static void emitLine(PoseStack.Pose pose,
                                 VertexConsumer consumer,
                                 double x1, double y1, double z1,
                                 double x2, double y2, double z2,
                                 float r, float g, float b, float a,
                                 float nx, float ny, float nz) {
        consumer.addVertex(pose, (float) x1, (float) y1, (float) z1)
            .setColor(r, g, b, a)
            .setNormal(pose, nx, ny, nz);
        consumer.addVertex(pose, (float) x2, (float) y2, (float) z2)
            .setColor(r, g, b, a)
            .setNormal(pose, nx, ny, nz);
    }
}
