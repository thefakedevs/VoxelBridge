package com.voxelbridge.export.exporter;

import net.minecraft.core.Direction;

/**
 * Tracks per-plane quad order and applies a tiny offset to later quads to reduce z-fighting.
 */
public final class PlaneOffsetTracker {
    private final com.voxelbridge.core.util.geometry.PlaneOffsetTrackerCore core =
        new com.voxelbridge.core.util.geometry.PlaneOffsetTrackerCore();

    public void clear() {
        core.clear();
    }

    public void applyOffset(float[] positions, float[] normal) {
        core.applyOffset(positions, normal);
    }

    public void applyOffset(float[] positions, float[] normal, Direction dir) {
        if (dir == null) {
            core.applyOffset(positions, normal);
            return;
        }
        core.applyOffset(positions, normal, dir.getStepX(), dir.getStepY(), dir.getStepZ(), true);
    }
}
