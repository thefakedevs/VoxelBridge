package com.voxelbridge.core.scene;

import java.util.List;

/**
 * Optional bulk-quad ingestion for high-throughput scene sinks.
 */
public interface BulkQuadSink {
    void addBatch(String materialGroupKey,
                  List<String> spriteKeys,
                  List<String> overlaySpriteKeys,
                  float[] flatPositions,
                  float[] flatUv0s,
                  float[] flatUv1s,
                  float[] flatNormals,
                  float[] flatColors,
                  List<Boolean> doubleSideds);
}
