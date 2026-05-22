package com.voxelbridge.compat;

import com.voxelbridge.mixin.TextureAtlasAccessor;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

public final class ForgeAtlasAccess {
    private ForgeAtlasAccess() {
    }

    public static Collection<TextureAtlasSprite> getAllSprites(TextureAtlas atlas) {
        if (atlas == null) {
            return Collections.emptyList();
        }
        try {
            Map<?, TextureAtlasSprite> map = ((TextureAtlasAccessor) atlas).voxelbridge$getTexturesByName();
            return map != null ? map.values() : Collections.emptyList();
        } catch (Throwable ignored) {
            return Collections.emptyList();
        }
    }
}
