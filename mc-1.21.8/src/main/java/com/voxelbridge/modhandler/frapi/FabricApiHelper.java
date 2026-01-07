package com.voxelbridge.modhandler.frapi;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Fabric Rendering API helper (stub for 1.21.8).
 * The Forgified Fabric API does not yet target 1.21.8, so this is a no-op.
 */
public final class FabricApiHelper {

    private FabricApiHelper() {}

    public static List<BakedQuad> extractQuads(
        Object model,
        BlockAndTintGetter level,
        BlockState state,
        BlockPos pos,
        RandomSource rand,
        Object spriteFinder
    ) {
        return List.of();
    }
}
