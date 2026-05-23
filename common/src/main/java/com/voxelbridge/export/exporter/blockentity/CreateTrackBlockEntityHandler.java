package com.voxelbridge.export.exporter.blockentity;

import com.voxelbridge.core.ir.IrSink;
import com.voxelbridge.export.ExportContext;
import com.voxelbridge.util.debug.LogModule;
import com.voxelbridge.util.debug.VoxelBridgeLogger;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Create curved tracks are normally drawn by Flywheel, so the normal
 * BlockEntityRenderer can early-out and produce no captured geometry.
 */
public final class CreateTrackBlockEntityHandler implements BlockEntityHandler {
    private static final String CREATE_TRACK_BE = "com.simibubi.create.content.trains.track.TrackBlockEntity";

    @Override
    public BlockEntityExportResult export(
        ExportContext ctx,
        Level level,
        BlockState state,
        BlockEntity blockEntity,
        BlockPos pos,
        IrSink sceneSink,
        double offsetX,
        double offsetY,
        double offsetZ,
        BlockEntityRenderBatch renderBatch
    ) {
        if (!isCreateTrackBlockEntity(blockEntity)) {
            return BlockEntityExportResult.NOT_HANDLED;
        }

        BlockEntityRenderer.CreateTrackConnectionsRenderTask task =
            BlockEntityRenderer.createCreateTrackConnectionsTask(
                ctx,
                blockEntity,
                sceneSink,
                pos.getX() + offsetX,
                pos.getY() + offsetY,
                pos.getZ() + offsetZ
            );

        if (task == null) {
            return BlockEntityExportResult.NOT_HANDLED;
        }

        if (renderBatch != null) {
            renderBatch.enqueue(task);
            VoxelBridgeLogger.debug(LogModule.BLOCKENTITY,
                "[CreateTrackBlockEntityHandler] Track connection task enqueued");
            return BlockEntityExportResult.RENDERED_KEEP_BLOCK;
        }

        task.run();
        if (task.wasSuccessful()) {
            return BlockEntityExportResult.RENDERED_KEEP_BLOCK;
        }
        return BlockEntityExportResult.NOT_HANDLED;
    }

    private boolean isCreateTrackBlockEntity(BlockEntity blockEntity) {
        Class<?> type = blockEntity.getClass();
        while (type != null) {
            if (CREATE_TRACK_BE.equals(type.getName())) {
                return true;
            }
            type = type.getSuperclass();
        }
        return false;
    }
}
