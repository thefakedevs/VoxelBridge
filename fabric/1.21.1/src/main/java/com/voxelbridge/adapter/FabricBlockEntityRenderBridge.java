package com.voxelbridge.adapter;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Fabric 1.21.1 block entity render bridge (legacy signature).
 */
public final class FabricBlockEntityRenderBridge implements BlockEntityRenderBridge {

    @Override
    public void render(net.minecraft.client.renderer.blockentity.BlockEntityRenderer renderer,
            BlockEntity blockEntity,
            float partial,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            int packedOverlay,
            Vec3 cameraPos) {
        renderer.render(blockEntity, partial, poseStack, buffer, packedLight, packedOverlay);
    }
}
