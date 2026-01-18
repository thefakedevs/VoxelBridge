package com.voxelbridge.adapter;

import com.voxelbridge.export.ExportContext;
import com.voxelbridge.modhandler.ModHandledQuads;
import com.voxelbridge.modhandler.ModHandlerRegistry;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public final class NeoForgeModHandlerBridge implements ModHandlerBridge {
    @Override
    public boolean shouldHandle(BlockEntity blockEntity) {
        return ModHandlerRegistry.shouldHandle(blockEntity);
    }

    @Override
    public List<BakedQuad> handle(
        ExportContext ctx,
        Level level,
        BlockState state,
        BlockEntity blockEntity,
        BlockPos pos,
        Object bakedModel
    ) {
        ModHandledQuads handled = ModHandlerRegistry.handle(ctx, level, state, blockEntity, pos, bakedModel);
        return handled != null ? handled.quads() : null;
    }
}
