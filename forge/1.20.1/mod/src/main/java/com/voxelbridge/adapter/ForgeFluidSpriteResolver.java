package com.voxelbridge.adapter;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.material.FluidState;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;

public final class ForgeFluidSpriteResolver implements FluidSpriteResolver {
    @Override
    public TextureAtlasSprite[] resolve(BlockAndTintGetter level, BlockPos pos, FluidState fluidState) {
        if (fluidState == null) {
            return null;
        }
        try {
            IClientFluidTypeExtensions extension = IClientFluidTypeExtensions.of(fluidState);
            TextureAtlas atlas = Minecraft.getInstance().getModelManager().getAtlas(TextureAtlas.LOCATION_BLOCKS);
            ResourceLocation still = extension.getStillTexture(fluidState, level, pos);
            ResourceLocation flowing = extension.getFlowingTexture(fluidState, level, pos);
            ResourceLocation missing = MissingTextureAtlasSprite.getLocation();
            TextureAtlasSprite stillSprite = atlas.getSprite(still != null ? still : missing);
            TextureAtlasSprite flowingSprite = atlas.getSprite(flowing != null ? flowing : (still != null ? still : missing));
            return new TextureAtlasSprite[] { stillSprite, flowingSprite };
        } catch (Throwable ignored) {
            return null;
        }
    }
}
