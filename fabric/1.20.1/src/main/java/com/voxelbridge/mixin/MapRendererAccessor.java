package com.voxelbridge.mixin;

import net.minecraft.client.gui.MapRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Access MapRenderer internal map cache to fetch map instances.
 */
@Mixin(MapRenderer.class)
public interface MapRendererAccessor {

    @Accessor("maps")
    it.unimi.dsi.fastutil.ints.Int2ObjectMap<?> voxelbridge$getMaps();
}
