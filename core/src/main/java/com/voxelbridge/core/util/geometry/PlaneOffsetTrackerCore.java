package com.voxelbridge.core.util.geometry;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

/**
 * Tracks per-plane quad order and applies a tiny offset to later quads
 * to reduce z-fighting. Minecraft-agnostic.
 * <p>
 * Updated to use "Plane + Spatial Bucket + 2D AABB overlap":
 * Quads are only considered colliding if they share the same plane, fall in the same spatial bucket,
 * and their projected 2D AABBs overlap with positive area.
 */
public final class PlaneOffsetTrackerCore {
    // Increased precision to 1e6 (0.001mm) to further prevent false positives
    // "EPS" in this context is effectively 1/QUANT. Higher QUANT = Smaller EPS = Stricter check.
    private static final float NORMAL_QUANT = 1000000f;
    private static final float DIST_QUANT = 1000000f;
    private static final float POS_QUANT = 1000000f; 
    private static final float OFFSET_STEP = 2e-4f;

    private final Long2ObjectOpenHashMap<ObjectArrayList<float[]>> buckets = new Long2ObjectOpenHashMap<>();

    public PlaneOffsetTrackerCore() {
        buckets.defaultReturnValue(null);
    }

    public void clear() {
        buckets.clear();
    }

    public void applyOffset(float[] positions, float[] normal) {
        applyOffset(positions, normal, 0f, 0f, 0f, false);
    }

    public void applyOffset(float[] positions, float[] normal, float dirX, float dirY, float dirZ, boolean hasDir) {
        if (positions == null || positions.length < 12 || normal == null || normal.length < 3) {
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

        // Plane Distance D
        // d = nx*x + ny*y + nz*z
        // Use first vertex for plane D calculation
        float d = nx * positions[0] + ny * positions[1] + nz * positions[2];

        // SPATIAL BUCKETING
        // To prevent infinite offset accumulation across the world for coplanar faces (e.g. floor),
        // we bucket faces into coarse spatial cells.
        // Cell Size = 2.0 ensures that faces within a ~2 block radius interact, but far faces do not.
        float cx = (positions[0] + positions[3] + positions[6] + positions[9]) * 0.25f;
        float cy = (positions[1] + positions[4] + positions[7] + positions[10]) * 0.25f;
        float cz = (positions[2] + positions[5] + positions[8] + positions[11]) * 0.25f;
        
        // Quantize centroid to spatial buckets
        int gridX = Math.round(cx / 2.0f);
        int gridY = Math.round(cy / 2.0f);
        int gridZ = Math.round(cz / 2.0f);

        int qnx = Math.round(nx * NORMAL_QUANT);
        int qny = Math.round(ny * NORMAL_QUANT);
        int qnz = Math.round(nz * NORMAL_QUANT);
        int qd = Math.round(d * DIST_QUANT);

        long planeHash = hash4(qnx, qny, qnz, qd);
        long gridHash = hash3(gridX, gridY, gridZ);
        
        // Combine plane hash and grid hash
        long key = planeHash ^ (gridHash * 31);
        
        float[] aabb2d = projectAabb2d(positions, nx, ny, nz);
        ObjectArrayList<float[]> list = buckets.get(key);
        int overlapCount = 0;
        if (list == null) {
            list = new ObjectArrayList<>();
            buckets.put(key, list);
        } else {
            for (int i = 0; i < list.size(); i++) {
                if (overlaps2d(aabb2d, list.get(i))) {
                    overlapCount++;
                }
            }
        }
        list.add(aabb2d);
        if (overlapCount == 0) {
            return;
        }

        float offset = OFFSET_STEP * overlapCount;
        for (int i = 0; i < 4; i++) {
            positions[i * 3] += nx * offset;
            positions[i * 3 + 1] += ny * offset;
            positions[i * 3 + 2] += nz * offset;
        }
    }

    private static long hash4(int a, int b, int c, int d) {
        long h = 1469598103934665603L;
        h = (h ^ a) * 1099511628211L;
        h = (h ^ b) * 1099511628211L;
        h = (h ^ c) * 1099511628211L;
        h = (h ^ d) * 1099511628211L;
        return h;
    }

    private static long hash3(int a, int b, int c) {
        long h = 1469598103934665603L;
        h = (h ^ a) * 1099511628211L;
        h = (h ^ b) * 1099511628211L;
        h = (h ^ c) * 1099511628211L;
        return h;
    }

    private static float[] projectAabb2d(float[] positions, float nx, float ny, float nz) {
        float anx = Math.abs(nx);
        float any = Math.abs(ny);
        float anz = Math.abs(nz);
        int axis; // 0 -> normal is X, project to YZ; 1 -> normal is Y, project to XZ; 2 -> normal is Z, project to XY
        if (anx >= any && anx >= anz) {
            axis = 0;
        } else if (any >= anz) {
            axis = 1;
        } else {
            axis = 2;
        }

        float minU = Float.POSITIVE_INFINITY;
        float maxU = Float.NEGATIVE_INFINITY;
        float minV = Float.POSITIVE_INFINITY;
        float maxV = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < 4; i++) {
            float x = positions[i * 3];
            float y = positions[i * 3 + 1];
            float z = positions[i * 3 + 2];
            float u;
            float v;
            if (axis == 0) { // YZ
                u = y;
                v = z;
            } else if (axis == 1) { // XZ
                u = x;
                v = z;
            } else { // XY
                u = x;
                v = y;
            }
            if (u < minU) minU = u;
            if (u > maxU) maxU = u;
            if (v < minV) minV = v;
            if (v > maxV) maxV = v;
        }
        return new float[]{minU, maxU, minV, maxV};
    }

    private static boolean overlaps2d(float[] a, float[] b) {
        return a[1] > b[0] && a[0] < b[1] && a[3] > b[2] && a[2] < b[3];
    }
}
