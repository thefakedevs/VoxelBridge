package com.voxelbridge.adapter;

import com.voxelbridge.mixin.CompositeRenderTypeAccessor;
import com.voxelbridge.mixin.CompositeStateAccessor;
import com.voxelbridge.mixin.EmptyTextureStateShardAccessor;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * NeoForge implementation of PlatformRenderHelper.
 */
public class NeoForgePlatformRenderHelper implements PlatformRenderHelper {

    // RenderType helpers
    @Override
    public ResourceLocation getRenderTypeTexture(RenderType renderType) {
        if (renderType == null)
            return null;

        ResourceLocation fromState = extractFromState(renderType);
        if (fromState != null) {
            return sanitize(fromState);
        }

        ResourceLocation fromString = extractFromString(renderType);
        if (fromString != null) {
            return sanitize(fromString);
        }

        return null;
    }

    @Override
    public boolean isRenderTypeDoubleSided(RenderType renderType) {
        RenderType.CompositeState state = compositeState(renderType);
        if (state == null) {
            return false;
        }
        var states = ((CompositeStateAccessor) (Object) state).voxelbridge$getStates();
        if (states == null) {
            return false;
        }
        for (RenderStateShard shard : states) {
            if (shard == null) {
                continue;
            }
            String name = shard.toString();
            if (name == null) {
                continue;
            }
            String lower = name.toLowerCase(java.util.Locale.ROOT);
            if (lower.contains("cull")) {
                return lower.contains("no_cull") || lower.contains("nocull");
            }
        }
        return false;
    }

    // --- Private helpers moved from RenderTypeTextureResolver ---

    private static ResourceLocation extractFromState(RenderType renderType) {
        RenderType.CompositeState state = compositeState(renderType);
        if (state == null) {
            return null;
        }
        RenderStateShard.EmptyTextureStateShard textureState =
                ((CompositeStateAccessor) (Object) state).voxelbridge$getTextureState();
        if (textureState == null) {
            return null;
        }
        Optional<ResourceLocation> result =
                ((EmptyTextureStateShardAccessor) (Object) textureState).voxelbridge$cutoutTexture();
        return result != null ? result.orElse(null) : null;
    }

    private static ResourceLocation extractFromString(RenderType renderType) {
        try {
            String name = renderType.toString();
            if (name.contains("RenderType[")) {
                int texIdx = name.indexOf("texture=");
                if (texIdx >= 0) {
                    int start = texIdx + 8;
                    int end = name.indexOf(",", start);
                    if (end < 0) {
                        end = name.indexOf("]", start);
                    }
                    if (end > start) {
                        String texStr = name.substring(start, end).trim();
                        return ResourceLocation.parse(texStr);
                    }
                }
            }
            String optional = parseOptionalTexture(name);
            if (optional != null) {
                return ResourceLocation.parse(optional);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static String parseOptionalTexture(String renderTypeString) {
        if (renderTypeString == null) {
            return null;
        }
        String marker = "texture[Optional[";
        int start = renderTypeString.indexOf(marker);
        if (start < 0) {
            return null;
        }
        int valueStart = start + marker.length();
        int valueEnd = renderTypeString.indexOf("]", valueStart);
        if (valueEnd <= valueStart) {
            return null;
        }
        String tex = renderTypeString.substring(valueStart, valueEnd).trim();
        if (tex.isEmpty() || "empty".equals(tex)) {
            return null;
        }
        return tex;
    }

    private static RenderType.CompositeState compositeState(RenderType renderType) {
        if (!(renderType instanceof CompositeRenderTypeAccessor composite)) {
            return null;
        }
        return composite.voxelbridge$getState();
    }

    private static ResourceLocation sanitize(ResourceLocation loc) {
        if (loc == null)
            return null;
        String namespace = loc.getNamespace();
        String path = loc.getPath();
        if (namespace.contains(":"))
            namespace = namespace.replace(':', '_');
        if (path.contains(":"))
            path = path.replace(':', '/');
        if (namespace.equals(loc.getNamespace()) && path.equals(loc.getPath()))
            return loc;
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }

    @Override
    public boolean isOnRenderThread() {
        return com.mojang.blaze3d.systems.RenderSystem.isOnRenderThread();
    }

    @Override
    public void recordRenderCall(Runnable task) {
        com.mojang.blaze3d.systems.RenderSystem.recordRenderCall(task::run);
    }

    @Override
    public net.minecraft.world.phys.Vec3 getBlockOffset(net.minecraft.world.level.block.state.BlockState state,
            net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos) {
        if (state == null || pos == null)
            return net.minecraft.world.phys.Vec3.ZERO;
        return state.getOffset(level, pos);
    }

    @Override
    public boolean isSolidRender(net.minecraft.world.level.block.state.BlockState state,
            net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos) {
        if (state == null)
            return false;
        return state.isSolidRender(level, pos);
    }

    @Override
    public com.mojang.blaze3d.vertex.PoseStack getGuiPose(net.minecraft.client.gui.GuiGraphics gfx) {
        if (gfx == null)
            return null;
        return gfx.pose();
    }

    @Override
    public void pushPose(com.mojang.blaze3d.vertex.PoseStack pose) {
        if (pose != null)
            pose.pushPose();
    }

    @Override
    public void popPose(com.mojang.blaze3d.vertex.PoseStack pose) {
        if (pose != null)
            pose.popPose();
    }

    @Override
    public void translatePose(com.mojang.blaze3d.vertex.PoseStack pose, float x, float y, float z) {
        if (pose != null)
            pose.translate(x, y, z);
    }
}
