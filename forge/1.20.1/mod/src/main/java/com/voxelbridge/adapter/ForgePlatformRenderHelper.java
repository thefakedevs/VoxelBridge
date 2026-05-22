package com.voxelbridge.adapter;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

public final class ForgePlatformRenderHelper implements PlatformRenderHelper {
    @Override
    public ResourceLocation getRenderTypeTexture(RenderType renderType) {
        if (renderType == null) {
            return null;
        }
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
                        String texStr = normalizeTextureToken(name.substring(start, end).trim());
                        if (texStr != null) {
                            return new ResourceLocation(texStr);
                        }
                    }
                }
            }
            String optional = normalizeTextureToken(parseOptionalTexture(name));
            return optional != null ? new ResourceLocation(optional) : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    @Override
    public boolean isRenderTypeDoubleSided(RenderType renderType) {
        if (renderType == null) {
            return false;
        }
        String lower = renderType.toString().toLowerCase(java.util.Locale.ROOT);
        return lower.contains("cull") && (lower.contains("no_cull") || lower.contains("nocull"));
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
        return tex.isEmpty() || "empty".equals(tex) ? null : tex;
    }

    private static String normalizeTextureToken(String token) {
        if (token == null) {
            return null;
        }
        String tex = token.trim();
        if (tex.isEmpty() || "empty".equalsIgnoreCase(tex)) {
            return null;
        }
        if (tex.startsWith("Optional[")) {
            int end = tex.lastIndexOf(']');
            if (end > "Optional[".length()) {
                tex = tex.substring("Optional[".length(), end).trim();
            }
        }
        return tex.isEmpty() ? null : tex;
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
            net.minecraft.world.level.Level level,
            net.minecraft.core.BlockPos pos) {
        return state != null && pos != null ? state.getOffset(level, pos) : net.minecraft.world.phys.Vec3.ZERO;
    }

    @Override
    public boolean isSolidRender(net.minecraft.world.level.block.state.BlockState state,
            net.minecraft.world.level.Level level,
            net.minecraft.core.BlockPos pos) {
        return state != null && state.isSolidRender(level, pos);
    }

    @Override
    public com.mojang.blaze3d.vertex.PoseStack getGuiPose(net.minecraft.client.gui.GuiGraphics gfx) {
        return gfx != null ? gfx.pose() : null;
    }

    @Override
    public void pushPose(com.mojang.blaze3d.vertex.PoseStack pose) {
        if (pose != null) {
            pose.pushPose();
        }
    }

    @Override
    public void popPose(com.mojang.blaze3d.vertex.PoseStack pose) {
        if (pose != null) {
            pose.popPose();
        }
    }

    @Override
    public void translatePose(com.mojang.blaze3d.vertex.PoseStack pose, float x, float y, float z) {
        if (pose != null) {
            pose.translate(x, y, z);
        }
    }

    @Override
    public int drawString(net.minecraft.client.gui.GuiGraphics gfx,
            net.minecraft.client.gui.Font font,
            String text,
            int x,
            int y,
            int color,
            boolean shadow) {
        return gfx != null && font != null && text != null ? gfx.drawString(font, text, x, y, color, shadow) : 0;
    }

    @Override
    public int drawString(net.minecraft.client.gui.GuiGraphics gfx,
            net.minecraft.client.gui.Font font,
            net.minecraft.network.chat.Component text,
            int x,
            int y,
            int color,
            boolean shadow) {
        return gfx != null && font != null && text != null ? gfx.drawString(font, text, x, y, color, shadow) : 0;
    }
}
