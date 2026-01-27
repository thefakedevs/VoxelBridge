package com.voxelbridge.core.util.geometry;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
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
    // "EPS" in this context is effectively 1/QUANT. Higher QUANT = Smaller EPS = Stricter check.
    private static final float DEFAULT_NORMAL_QUANT = 1000000f;
    private static final float DEFAULT_DIST_QUANT = 1000000f;
    private static final float DEFAULT_POS_QUANT = 1000000f;
    private static final float OFFSET_STEP = 2e-4f;

    private static final float DEFAULT_BUCKET_EPS = 1e-3f;
    private static final float DEFAULT_CELL_SIZE = 3.0f;
    private static final float DEFAULT_OVERLAP_EPS = 1e-3f;

    private final float cellSize;
    private final float bucketEps;
    private final float overlapEps;
    private final float normalQuant;
    private final float distQuant;
    private final float posQuant;

    private static final class Entry {
        final int id;
        final float[] aabb2d;

        private Entry(int id, float[] aabb2d) {
            this.id = id;
            this.aabb2d = aabb2d;
        }
    }

    private final Int2ObjectOpenHashMap<ObjectArrayList<Entry>> buckets = new Int2ObjectOpenHashMap<>();
    private int nextEntryId = 1;

    public PlaneOffsetTrackerCore() {
        this(DEFAULT_CELL_SIZE, DEFAULT_BUCKET_EPS, DEFAULT_OVERLAP_EPS, DEFAULT_NORMAL_QUANT, DEFAULT_DIST_QUANT, DEFAULT_POS_QUANT);
    }

    public PlaneOffsetTrackerCore(float cellSize, float bucketEps, float overlapEps) {
        this(cellSize, bucketEps, overlapEps, DEFAULT_NORMAL_QUANT, DEFAULT_DIST_QUANT, DEFAULT_POS_QUANT);
    }

    public PlaneOffsetTrackerCore(float cellSize, float bucketEps, float overlapEps, float normalQuant, float distQuant, float posQuant) {
        buckets.defaultReturnValue(null);
        this.cellSize = cellSize;
        this.bucketEps = bucketEps;
        this.overlapEps = overlapEps;
        this.normalQuant = normalQuant;
        this.distQuant = distQuant;
        this.posQuant = posQuant;
    }

    public void clear() {
        buckets.clear();
        nextEntryId = 1;
    }

    public void applyOffset(float[] positions, float[] normal) {
        applyOffsetInternal(positions, normal, 0f, 0f, 0f, false, 0L, false);
    }

    public void applyOffset(float[] positions, float[] normal, float dirX, float dirY, float dirZ, boolean hasDir) {
        applyOffsetInternal(positions, normal, dirX, dirY, dirZ, hasDir, 0L, false);
    }

    public void applyOffsetWithBucketKey(float[] positions, float[] normal, float dirX, float dirY, float dirZ, boolean hasDir, long bucketKey) {
        applyOffsetInternal(positions, normal, dirX, dirY, dirZ, hasDir, bucketKey, true);
    }

    private void applyOffsetInternal(
        float[] positions,
        float[] normal,
        float dirX,
        float dirY,
        float dirZ,
        boolean hasDir,
        long bucketKey,
        boolean useBucketKey
    ) {
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

        float ox = nx;
        float oy = ny;
        float oz = nz;

        // Canonicalize normal so parallel (opposite) faces share the same plane hash.
        if (nx < 0f || (nx == 0f && ny < 0f) || (nx == 0f && ny == 0f && nz < 0f)) {
            nx = -nx;
            ny = -ny;
            nz = -nz;
        }

        // Plane Distance D
        // d = nx*x + ny*y + nz*z
        // Use first vertex for plane D calculation
        float d = nx * positions[0] + ny * positions[1] + nz * positions[2];

        // SPATIAL BUCKETING
        // To prevent infinite offset accumulation across the world for coplanar faces (e.g. floor),
        // we bucket faces into coarse spatial cells.
        // Cell Size = 3.0 groups faces within a 3x3x3 block region into the same bucket.
        float cx = (positions[0] + positions[3] + positions[6] + positions[9]) * 0.25f;
        float cy = (positions[1] + positions[4] + positions[7] + positions[10]) * 0.25f;
        float cz = (positions[2] + positions[5] + positions[8] + positions[11]) * 0.25f;
        
        // Quantize centroid to spatial buckets
        int gridX = Math.round(cx / cellSize);
        int gridY = Math.round(cy / cellSize);
        int gridZ = Math.round(cz / cellSize);

        int qnx = Math.round(nx * normalQuant);
        int qny = Math.round(ny * normalQuant);
        int qnz = Math.round(nz * normalQuant);
        int qd = Math.round(d * distQuant);

        int planeHash = hash4(qnx, qny, qnz, qd);
        float[] aabb2d = new float[4];
        projectAabb2d(positions, nx, ny, nz, aabb2d);

        int overlapCount = 0;
        if (useBucketKey) {
            int key = planeHash ^ (foldLongToInt(bucketKey) * 31);
            ObjectArrayList<Entry> list = buckets.get(key);
            if (list != null) {
                for (int i = 0; i < list.size(); i++) {
                    Entry entry = list.get(i);
                    if (overlaps2d(aabb2d, entry.aabb2d)) {
                        overlapCount++;
                    }
                }
            }
            Entry entry = new Entry(nextEntryId++, aabb2d);
            if (list == null) {
                list = new ObjectArrayList<>();
                buckets.put(key, list);
            }
            list.add(entry);
        } else {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        int gridHash = foldLongToInt(packInt3(gridX + dx, gridY + dy, gridZ + dz));
                        int key = planeHash ^ (gridHash * 31);
                        ObjectArrayList<Entry> list = buckets.get(key);
                        if (list == null) {
                            continue;
                        }
                        for (int i = 0; i < list.size(); i++) {
                            Entry entry = list.get(i);
                            if (overlaps2d(aabb2d, entry.aabb2d)) {
                                overlapCount++;
                            }
                        }
                    }
                }
            }

            int gridHash = foldLongToInt(packInt3(gridX, gridY, gridZ));
            int key = planeHash ^ (gridHash * 31);
            ObjectArrayList<Entry> list = buckets.get(key);
            Entry entry = new Entry(nextEntryId++, aabb2d);
            if (list == null) {
                list = new ObjectArrayList<>();
                buckets.put(key, list);
            }
            list.add(entry);
        }
        if (overlapCount == 0) {
            return;
        }

        float offset = OFFSET_STEP * overlapCount;
        for (int i = 0; i < 4; i++) {
            positions[i * 3] += ox * offset;
            positions[i * 3 + 1] += oy * offset;
            positions[i * 3 + 2] += oz * offset;
        }
    }

    private static int hash4(int a, int b, int c, int d) {
        int h = 0x811c9dc5;
        h = (h ^ a) * 0x01000193;
        h = (h ^ b) * 0x01000193;
        h = (h ^ c) * 0x01000193;
        h = (h ^ d) * 0x01000193;
        return h;
    }

    private static long packInt3(int x, int y, int z) {
        long lx = ((long) x) & 0x1FFFFF;
        long ly = ((long) y) & 0x1FFFFF;
        long lz = ((long) z) & 0x1FFFFF;
        return (lx << 42) | (ly << 21) | lz;
    }

    private static int foldLongToInt(long value) {
        return (int) (value ^ (value >>> 32));
    }

    private static void projectAabb2d(float[] positions, float nx, float ny, float nz, float[] out) {
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
        out[0] = minU;
        out[1] = maxU;
        out[2] = minV;
        out[3] = maxV;
    }

    private boolean overlaps2d(float[] a, float[] b) {
        float eps = overlapEps;
        return a[1] + eps > b[0] && a[0] - eps < b[1] && a[3] + eps > b[2] && a[2] - eps < b[3];
    }
}
