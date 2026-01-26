package com.voxelbridge.adapter;

import com.mojang.blaze3d.vertex.PoseStack;
import com.voxelbridge.export.exporter.entity.EntityTextureResolverCompat;
import com.voxelbridge.export.exporter.resolve.TextureResolver;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * 1.21.4 entity render bridge (render-state pipeline).
 */
public final class NeoForgeEntityRenderBridge implements EntityRenderBridge {

    @Override
    public boolean shouldApplyHangingOffset() {
        return false;
    }

    @Override
    public TextureResolver<Entity> getTextureResolver() {
        return EntityTextureResolverCompat.INSTANCE;
    }

    @Override
    public Object createRenderState(net.minecraft.client.renderer.entity.EntityRenderer renderer,
                                    Entity entity,
                                    float yaw,
                                    float partial) {
        return renderer.createRenderState(entity, partial);
    }

    @Override
    public Vec3 getRenderOffset(net.minecraft.client.renderer.entity.EntityRenderer renderer,
                                Entity entity,
                                float partial,
                                Object renderState) {
        if (renderState instanceof EntityRenderState state) {
            return renderer.getRenderOffset(state);
        }
        return Vec3.ZERO;
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
        EntityRenderState state = renderState instanceof EntityRenderState s
            ? s
            : renderer.createRenderState(entity, partial);
        renderer.render(state, poseStack, buffer, packedLight);
    }
}
