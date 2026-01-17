package com.voxelbridge.adapter;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import java.util.List;

/**
 * Abstraction layer for rendering subsystem.
 * Isolates model retrieval, quad extraction, and texture resolution.
 */
public interface RenderAdapter {
    /**
     * Gets the baked model handle for a block state.
     */
    Object getBlockModel(BlockState state);

    /**
     * Extracts quads from a model.
     * This encapsulates the complexity of:
     * 1. Vanilla getQuads
     * 2. Fabric/NeoForge API extensions (CTM, Indigo, etc.)
     * 3. ModelData handling
     */
    List<BakedQuad> getQuads(Object model, BlockState state, BlockPos pos, BlockAndTintGetter level, long seed);

    /**
     * Resolves the unique resource location string for a texture sprite.
     * e.g., "minecraft:block/stone"
     */
    String getSpriteName(TextureAtlasSprite sprite);
}
