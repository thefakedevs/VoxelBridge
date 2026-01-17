package com.voxelbridge.core.util.geometry;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

/**
 * Tracks per-plane quad order and applies a tiny offset to later quads
 * to reduce z-fighting. Minecraft-agnostic.
 */
public final class PlaneOffsetTrackerCore {
    private static final float NORMAL_QUANT = 1000f;
    private static final float DIST_QUANT = 1000f;
    private static final float OFFSET_STEP = 2e-4f;

    private final Long2IntOpenHashMap counts = new Long2IntOpenHashMap();

    public PlaneOffsetTrackerCore() {
        counts.defaultReturnValue(0);
    }

    public void clear() {
        counts.clear();
    }

    public void applyOffset(float[] positions, float[] normal) {
        applyOffset(positions, normal, 0f, 0f, 0f, false);
    }

    public void applyOffset(float[] positions, float[] normal, float dirX, float dirY, float dirZ, boolean hasDir) {
        if (positions == null || positions.length < 3 || normal == null || normal.length < 3) {
            return;
        }

        float nx = normal[0];
        float ny = normal[1];
        float nz = normal[2];
        float lenSq = nx * nx + ny * ny + nz * nz;
        if (lenSq < 1e-6f) {
            return;
        }

        float invLen = 1f / (float) Math.sqrt(lenSq);
        nx *= invLen;
        ny *= invLen;
        nz *= invLen;

        if (hasDir) {
            float dot = nx * dirX + ny * dirY + nz * dirZ;
            if (dot < 0f) {
                nx = -nx;
                ny = -ny;
                nz = -nz;
            }
        }

        float d = nx * positions[0] + ny * positions[1] + nz * positions[2];
        int qnx = Math.round(nx * NORMAL_QUANT);
        int qny = Math.round(ny * NORMAL_QUANT);
        int qnz = Math.round(nz * NORMAL_QUANT);
        int qd = Math.round(d * DIST_QUANT);

        long key = hash(qnx, qny, qnz, qd);
        int index = counts.get(key);
        counts.put(key, index + 1);
        if (index == 0) {
            return;
        }

        float offset = OFFSET_STEP * index;
        for (int i = 0; i < 4; i++) {
            positions[i * 3] += nx * offset;
            positions[i * 3 + 1] += ny * offset;
            positions[i * 3 + 2] += nz * offset;
        }
    }

    private static long hash(int a, int b, int c, int d) {
        long h = 1469598103934665603L;
        h = (h ^ a) * 1099511628211L;
        h = (h ^ b) * 1099511628211L;
        h = (h ^ c) * 1099511628211L;
        h = (h ^ d) * 1099511628211L;
        return h;
    }
}
