package com.voxelbridge.adapter;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.voxelbridge.export.ExportControl;
import com.voxelbridge.export.ExportProgressTracker;
import com.voxelbridge.export.ExportProgressTracker.ChunkState;
import com.voxelbridge.platform.client.ClientAccessHolder;
import com.voxelbridge.platform.forge.ForgeEventBusBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.IEventBus;

import java.util.Map;

public final class ForgeSelectionRenderBridge implements SelectionRenderBridge {
    @Override
    public void register(Object gameBus) {
        IEventBus bus = ForgeEventBusBridge.cast(gameBus);
        if (bus != null) {
            bus.addListener(this::onRenderLevel);
        }
    }

    private void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }

        BlockPos pos1 = ExportControl.getPos1();
        BlockPos pos2 = ExportControl.getPos2();
        if (pos1 == null && pos2 == null) {
            return;
        }

        Minecraft mc = ClientAccessHolder.get().getMinecraft();
        Vec3 camPos = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.lines());

        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);

        if (pos1 != null) {
            renderBox(poseStack, consumer, pos1, 1.0f, 0.2f, 0.2f, 0.6f);
        }
        if (pos2 != null) {
            renderBox(poseStack, consumer, pos2, 0.2f, 0.2f, 1.0f, 0.6f);
        }
        if (pos1 != null && pos2 != null) {
            renderSelectionBox(poseStack, consumer, pos1, pos2, 0.0f, 1.0f, 1.0f, 0.5f);
            renderChunkStatus(poseStack, consumer, pos1, pos2);
        }

        poseStack.popPose();
        bufferSource.endBatch(RenderType.lines());
    }

    private static void renderBox(PoseStack poseStack, VertexConsumer consumer,
            BlockPos pos, float r, float g, float b, float a) {
        LevelRenderer.renderLineBox(poseStack, consumer, new AABB(pos).inflate(0.002), r, g, b, a);
    }

    private static void renderSelectionBox(PoseStack poseStack, VertexConsumer consumer,
            BlockPos pos1, BlockPos pos2, float r, float g, float b, float a) {
        int minX = Math.min(pos1.getX(), pos2.getX());
        int minY = Math.min(pos1.getY(), pos2.getY());
        int minZ = Math.min(pos1.getZ(), pos2.getZ());
        int maxX = Math.max(pos1.getX(), pos2.getX()) + 1;
        int maxY = Math.max(pos1.getY(), pos2.getY()) + 1;
        int maxZ = Math.max(pos1.getZ(), pos2.getZ()) + 1;
        LevelRenderer.renderLineBox(poseStack, consumer, new AABB(minX, minY, minZ, maxX, maxY, maxZ), r, g, b, a);
    }

    private static void renderChunkStatus(PoseStack poseStack, VertexConsumer consumer, BlockPos pos1, BlockPos pos2) {
        var states = ExportProgressTracker.snapshot();
        if (states.isEmpty()) {
            return;
        }

        int selMinX = Math.min(pos1.getX(), pos2.getX());
        int selMinY = Math.min(pos1.getY(), pos2.getY());
        int selMinZ = Math.min(pos1.getZ(), pos2.getZ());
        int selMaxX = Math.max(pos1.getX(), pos2.getX()) + 1;
        int selMaxY = Math.max(pos1.getY(), pos2.getY()) + 1;
        int selMaxZ = Math.max(pos1.getZ(), pos2.getZ()) + 1;

        for (Map.Entry<Long, ChunkState> entry : states.entrySet()) {
            int chunkX = ChunkPos.getX(entry.getKey());
            int chunkZ = ChunkPos.getZ(entry.getKey());
            int minX = chunkX << 4;
            int minZ = chunkZ << 4;
            int maxX = minX + 16;
            int maxZ = minZ + 16;

            int boxMinX = Math.max(minX, selMinX);
            int boxMinY = selMinY;
            int boxMinZ = Math.max(minZ, selMinZ);
            int boxMaxX = Math.min(maxX, selMaxX);
            int boxMaxY = selMaxY;
            int boxMaxZ = Math.min(maxZ, selMaxZ);
            if (boxMinX >= boxMaxX || boxMinY >= boxMaxY || boxMinZ >= boxMaxZ) {
                continue;
            }

            float r;
            float g;
            float b;
            if (entry.getValue() == ChunkState.DONE) {
                r = 0.1f;
                g = 1.0f;
                b = 0.1f;
            } else if (entry.getValue() == ChunkState.RUNNING) {
                r = 1.0f;
                g = 0.8f;
                b = 0.1f;
            } else {
                r = 1.0f;
                g = 0.2f;
                b = 0.2f;
            }

            LevelRenderer.renderLineBox(poseStack, consumer,
                    new AABB(boxMinX, boxMinY, boxMinZ, boxMaxX, boxMaxY, boxMaxZ),
                    r, g, b, 0.35f);
        }
    }
}
