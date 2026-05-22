package com.voxelbridge.adapter;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public final class ForgeEntityRenderBridge implements EntityRenderBridge {
    @Override
    public boolean shouldApplyHangingOffset() {
        return false;
    }

    @Override
    public Object createRenderState(net.minecraft.client.renderer.entity.EntityRenderer renderer,
            Entity entity,
            float yaw,
            float partial) {
        return null;
    }

    @Override
    public Vec3 getRenderOffset(net.minecraft.client.renderer.entity.EntityRenderer renderer,
            Entity entity,
            float partial,
            Object renderState) {
        return renderer.getRenderOffset(entity, partial);
    }

    @Override
    public void render(net.minecraft.client.renderer.entity.EntityRenderer renderer,
            Object renderState,
            Entity entity,
            float yaw,
            float partial,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight) {
        renderer.render(entity, yaw, partial, poseStack, buffer, packedLight);
    }
}
