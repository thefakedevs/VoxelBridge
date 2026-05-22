package com.voxelbridge.compat;

import com.mojang.blaze3d.platform.NativeImage;
import com.voxelbridge.mixin.SpriteContentsAccessor;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

public final class ForgeSpriteAccess {
    private ForgeSpriteAccess() {
    }

    public static NativeImage getOriginalImage(TextureAtlasSprite sprite) {
        if (sprite == null || sprite.contents() == null) {
            return null;
        }
        try {
            return ((SpriteContentsAccessor) sprite.contents()).voxelbridge$getOriginalImage();
        } catch (Throwable ignored) {
            return null;
        }
    }
}
