package com.voxelbridge.mixin;

import net.fabricmc.fabric.impl.client.indigo.renderer.render.BlockRenderInfo;
import net.minecraft.util.RandomSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BlockRenderInfo.class)
public interface BlockRenderInfoAccessor {
    @Accessor(value = "random", remap = false)
    void voxelbridge$setRandom(RandomSource random);

    @Accessor(value = "seed", remap = false)
    void voxelbridge$setSeed(long seed);

    @Accessor(value = "recomputeSeed", remap = false)
    void voxelbridge$setRecomputeSeed(boolean recomputeSeed);

    @Accessor(value = "useAo", remap = false)
    void voxelbridge$setUseAo(boolean useAo);

    @Accessor(value = "defaultAo", remap = false)
    void voxelbridge$setDefaultAo(boolean defaultAo);
}
