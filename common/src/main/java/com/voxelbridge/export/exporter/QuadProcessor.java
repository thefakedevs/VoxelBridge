package com.voxelbridge.export.exporter;

import com.voxelbridge.core.ir.IrSink;
import com.voxelbridge.core.ir.RenderLayer;
import com.voxelbridge.core.ir.TintMode;
import com.voxelbridge.compat.QuadCompat;
import com.voxelbridge.core.util.color.ColorUtil;
import com.voxelbridge.core.util.color.ColorMode;
import com.voxelbridge.core.util.color.ColorModeHandler;
import com.voxelbridge.core.util.geometry.GeometryUtil;
import com.voxelbridge.export.ExportContext;
import com.voxelbridge.export.util.geometry.VertexExtractor;
import com.voxelbridge.platform.client.ClientAccessHolder;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Set;

/**
 * Processes individual quads and outputs them to the scene sink (PASS 2 logic).
 * Handles tint colors, PBR textures, and dynamic texture registration.
 */
public final class QuadProcessor {

    private final ExportContext ctx;
    private final Level level;
    private final IrSink sceneSink;
    private final double offsetX, offsetY, offsetZ;
    private final PlaneOffsetTracker planeOffset;

    // Placeholder to ensure tool reads the file first
    private final Set<String> pbrLoadedSprites = new HashSet<>();

    // Track processed quads to avoid duplicates (Optimization: Use FastUtil primitive set)
    private final it.unimi.dsi.fastutil.longs.LongOpenHashSet quadKeys = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();

    public QuadProcessor(ExportContext ctx, Level level, IrSink sceneSink,
                         double offsetX, double offsetY, double offsetZ,
                         PlaneOffsetTracker planeOffset) {
        this.ctx = ctx;
        this.level = level;
        this.sceneSink = sceneSink;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.offsetZ = offsetZ;
        this.planeOffset = planeOffset;
    }

    /**
     * Clears all caches. Call this before processing each block.
     */
    public void clear() {
        quadKeys.clear();
        // Note: pbrLoadedSprites is intentionally NOT cleared to avoid redundant loads
    }

    /**
     * Processes a single quad and outputs it to the scene sink.
     *
     * @param state block state
     * @param pos block position
     * @param quad the baked quad
     * @param blockKey material key for this block
     * @param randomOffset vanilla random offset
     */
    public void processQuad(BlockState state, BlockPos pos, BakedQuad quad,
                            String blockKey, Vec3 randomOffset) {
        TextureAtlasSprite sprite = QuadCompat.getSprite(quad);
        if (sprite == null) return;

        String spriteKey = ctx.getTextureAccess().resolveSpriteKey(sprite);

        // Load PBR textures (once per sprite)
        if (!pbrLoadedSprites.contains(spriteKey)) {
            ensurePbrTexturesCached(sprite, spriteKey);
            pbrLoadedSprites.add(spriteKey);
        }

        // Handle dynamic textures (CTM, numbered sprites)
        boolean isDynamic = spriteKey.matches(".*\\d+$") || !ctx.getMaterialPaths().containsKey(spriteKey);
        if (isDynamic) {
            com.voxelbridge.export.texture.TextureAtlasManager.registerTint(ctx, spriteKey, 0xFFFFFF);
            if (ctx.getCachedSpriteImage(spriteKey) == null) {
                try {
                    BufferedImage img = ctx.getTextureAccess().readSprite(sprite);
                    if (img != null) ctx.cacheSpriteImage(spriteKey, img);
                } catch (Exception ignore) {}
            }
        }

        // Extract vertex data
        VertexExtractor.VertexData vertexData = VertexExtractor.extractFromQuad(
            quad, pos, sprite, offsetX, offsetY, offsetZ, randomOffset
        );

        boolean doubleSided = state.getBlock() instanceof BushBlock;

        // Check for duplicates
        long quadKey = GeometryUtil.computeQuadKey(spriteKey.hashCode(), vertexData.positions(),
            vertexData.normal(), doubleSided, vertexData.uvs());
        if (!quadKeys.add(quadKey)) return;

        boolean hasBaked = ColorUtil.hasBakedColors(vertexData.colors());

        ColorModeHandler.ColorData colorData;

        if (hasBaked) {
            // Prefer baked vertex colors (e.g., FRAPI-provided tint) over vanilla tint.
            if (ctx.getColorMode() == ColorMode.COLORMAP) {
                int bakedTint = ColorUtil.extractBakedTintArgb(vertexData.colors());
                colorData = ColorModeHandler.prepareColors(
                    ctx.getColorMode(), ctx.getColorMapAccess(), bakedTint, true);
            } else {
                float[] linearColors = ColorUtil.convertArgbToLinearRgba(vertexData.colors());
                colorData = new ColorModeHandler.ColorData(null, linearColors);
            }
        } else {
            // Compute tint color (returns -1 if no tint found)
            int tintColor = computeTintColor(state, pos, quad);
            if (tintColor != -1) {
                // Found a valid block tint color
                colorData = ColorModeHandler.prepareColors(
                    ctx.getColorMode(), ctx.getColorMapAccess(), tintColor, true);
            } else {
                // Default to white
                colorData = ColorModeHandler.prepareColors(
                    ctx.getColorMode(), ctx.getColorMapAccess(), 0xFFFFFFFF, false);
            }
        }

        String materialKey = ctx.resolveMaterialKey(spriteKey, blockKey);
        // Register sprite material (Intern strings)
        ctx.registerSpriteMaterial(spriteKey, materialKey);

        if (planeOffset != null) {
            planeOffset.applyOffset(vertexData.positions(), vertexData.normal(), QuadCompat.getDirection(quad));
        }

        // Output quad (Intern keys)
        TintMode tintMode = ctx.getColorMode() == ColorMode.COLORMAP
            ? TintMode.COLORMAP
            : TintMode.VERTEX_COLOR;
        sceneSink.addQuad(ctx.intern(materialKey), ctx.intern(spriteKey), null,
            RenderLayer.UNKNOWN, tintMode, doubleSided, false,
            vertexData.positions(), vertexData.uvs(), colorData.uv1(), vertexData.normal(), colorData.colors());
    }

    /**
     * Computes tint color from block colors. Returns -1 if no tint logic exists.
     */
    private int computeTintColor(BlockState state, BlockPos pos, BakedQuad quad) {
        int tintIndex = QuadCompat.getTintIndex(quad);
        if (tintIndex < 0) return -1;
        return ClientAccessHolder.get().getMinecraft().getBlockColors().getColor(state, level, pos, tintIndex);
    }

    /**
     * Pre-loads PBR companion textures (normal and specular maps).
     */
    private void ensurePbrTexturesCached(TextureAtlasSprite sprite, String spriteKey) {
        if (sprite == null || spriteKey == null) return;
        // Use PbrTextureHelper's enhanced lookup logic
        com.voxelbridge.export.texture.PbrTextureHelper.ensurePbrCached(ctx, spriteKey, sprite);
    }

}
