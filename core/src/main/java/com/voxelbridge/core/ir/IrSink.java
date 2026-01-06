package com.voxelbridge.core.ir;

/**
 * IR sink for sampled geometry. Performance-oriented: flat parameters, no per-quad objects.
 */
public interface IrSink {
    void addQuad(String materialKey,
                 String spriteKey,
                 String overlaySpriteKey,
                 RenderLayer renderLayer,
                 TintMode tintMode,
                 boolean doubleSided,
                 boolean emissive,
                 float[] positions,
                 float[] uv0,
                 float[] uv1,
                 float[] normal,
                 float[] colors);

    default void onChunkStart(int chunkX, int chunkZ) {}

    default void onChunkEnd(int chunkX, int chunkZ, boolean successful) {}
}
