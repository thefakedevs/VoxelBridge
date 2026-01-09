package com.voxelbridge.export.texture;

import com.voxelbridge.core.export.ExportState;
import com.voxelbridge.util.debug.LogModule;
import com.voxelbridge.util.debug.VoxelBridgeLogger;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.awt.image.SinglePixelPackedSampleModel;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

/**
 * Core atlas builder: packs tinted sprites, writes atlas pages, and generates PBR atlases.
 */
public final class AtlasBuilder {
    private static final int MAX_TINT_SLOTS = 64 * 64;
    private static final int DEFAULT_TINT = 0xFFFFFF;

    private AtlasBuilder() {}

    public static void generateIndividualTextures(ExportOptions options,
                                                  ExportState state,
                                                  Path outDir,
                                                  Map<String, ExportState.TintAtlas> entries,
                                                  Map<String, BufferedImage> baseImages,
                                                  Map<String, BufferedImage> normalImages,
                                                  Map<String, BufferedImage> specImages,
                                                  Map<String, String> materialPaths) throws IOException {
        if (entries == null || entries.isEmpty()) {
            return;
        }

        long tIndividual = VoxelBridgeLogger.now();
        String subDir = "textures/individual/";
        for (Map.Entry<String, ExportState.TintAtlas> entry : entries.entrySet()) {
            String spriteKey = entry.getKey();
            ExportState.TintAtlas atlas = entry.getValue();
            atlas.placements.clear();

            ensureDefaultTint(atlas);

            int tintSlots = Math.max(1, Math.min(MAX_TINT_SLOTS, atlas.nextIndex.get()));
            int[] tintBySlot = resolveTintSlots(atlas, tintSlots);

            BufferedImage base = baseImages.get(spriteKey);
            if (base == null) {
                VoxelBridgeLogger.warn(LogModule.TEXTURE_ATLAS, String.format(
                    "[AtlasGen][WARN] sprite=%s missing texture, using placeholder", spriteKey));
                base = createMissingTexture();
            }

            BufferedImage outputImage = tintTile(base, tintBySlot[0]);
            String relativePath = subDir + TexturePathResolver.safe(spriteKey) + ".png";
            Path target = outDir.resolve(relativePath);
            Files.createDirectories(target.getParent());
            com.voxelbridge.core.texture.PngjWriter.write(outputImage, target);
            atlas.atlasFile = target;
            atlas.texW = outputImage.getWidth();
            atlas.texH = outputImage.getHeight();
            atlas.usesAtlas = false;
            atlas.cols = 1;
            atlas.placements.clear();
            materialPaths.put(spriteKey, relativePath);

            if (VoxelBridgeLogger.isDebugEnabled(LogModule.TEXTURE_ATLAS)) {
                VoxelBridgeLogger.info(LogModule.TEXTURE_ATLAS, String.format(
                    "[AtlasGen] sprite=%s mode=individual path=%s", spriteKey, relativePath));
            }

            BufferedImage normal = normalImages.get(spriteKey);
            if (normal != null) {
                String normalRel = subDir + TexturePathResolver.safe(spriteKey) + "_n.png";
                Path normalTarget = outDir.resolve(normalRel);
                Files.createDirectories(normalTarget.getParent());
                com.voxelbridge.core.texture.PngjWriter.write(normal, normalTarget);
                VoxelBridgeLogger.info(LogModule.TEXTURE_ATLAS, String.format(
                    "[AtlasGen] sprite=%s normal exported: %s", spriteKey, normalRel));
                materialPaths.put(spriteKey + "_n", normalRel);
            }
            BufferedImage spec = specImages.get(spriteKey);
            if (spec != null) {
                String specRel = subDir + TexturePathResolver.safe(spriteKey) + "_s.png";
                Path specTarget = outDir.resolve(specRel);
                Files.createDirectories(specTarget.getParent());
                com.voxelbridge.core.texture.PngjWriter.write(spec, specTarget);
                VoxelBridgeLogger.info(LogModule.TEXTURE_ATLAS, String.format(
                    "[AtlasGen] sprite=%s specular exported: %s", spriteKey, specRel));
                materialPaths.put(spriteKey + "_s", specRel);
            }
        }
        VoxelBridgeLogger.duration("individual_texture_write", VoxelBridgeLogger.elapsedSince(tIndividual));
    }

    private record AtlasRequest(String spriteKey, int tintIndex, BufferedImage image, int innerWidth, int innerHeight, int pad) {}
    private record TintTask(String spriteKey, int tintIndex, int tint) {}

    public static void generatePackedAtlas(ExportOptions options,
                                           ExportState state,
                                           Path outDir,
                                           Map<String, ExportState.TintAtlas> entries,
                                           Map<String, BufferedImage> baseImages,
                                           Map<String, BufferedImage> normalImages,
                                           Map<String, BufferedImage> specImages,
                                           Map<String, String> materialPaths,
                                           String atlasDirName,
                                           String atlasPrefix,
                                           int defaultNormalColor,
                                           int defaultSpecColor,
                                           int threadCount) throws IOException {
        if (entries == null || entries.isEmpty()) {
            return;
        }

        VoxelBridgeLogger.info(LogModule.TEXTURE_ATLAS, String.format(
            "[AtlasGen] Generating packed atlas with %d sprites", entries.size()));

        List<TintTask> tintTasks = new ArrayList<>();
        Map<Integer, String> pagePathMap = new HashMap<>();
        Map<String, AtlasRequest> requestByKey = new LinkedHashMap<>();
        Map<Integer, Integer> pageToUdim = new HashMap<>();

        for (Map.Entry<String, ExportState.TintAtlas> entry : entries.entrySet()) {
            String spriteKey = entry.getKey();
            ExportState.TintAtlas atlas = entry.getValue();
            atlas.placements.clear();

            ensureDefaultTint(atlas);

            int tintSlots = Math.max(1, Math.min(MAX_TINT_SLOTS, atlas.nextIndex.get()));
            int[] tintBySlot = resolveTintSlots(atlas, tintSlots);

            if (VoxelBridgeLogger.isDebugEnabled(LogModule.TEXTURE_ATLAS)) {
                VoxelBridgeLogger.info(LogModule.TEXTURE_ATLAS, String.format(
                    "[AtlasGen] Processing sprite: %s (tintSlots=%d)", spriteKey, tintSlots));
            }

            for (int i = 0; i < tintSlots; i++) {
                tintTasks.add(new TintTask(spriteKey, i, tintBySlot[i]));
            }
        }

        Set<String> uniqueSprites = tintTasks.stream()
            .map(TintTask::spriteKey)
            .collect(Collectors.toSet());

        Map<String, BufferedImage> preloaded = uniqueSprites.stream()
            .collect(Collectors.toConcurrentMap(
                key -> key,
                key -> {
                    BufferedImage base = baseImages.get(key);
                    if (base == null) {
                        VoxelBridgeLogger.warn(LogModule.TEXTURE_ATLAS, String.format(
                            "[AtlasGen][WARN] sprite=%s missing texture, using placeholder", key));
                        base = createMissingTexture();
                    }
                    return base;
                },
                (a, b) -> a
            ));

        int padding = options.atlasPadding();
        Map<String, boolean[]> baseMasks = new ConcurrentHashMap<>();
        if (padding > 0) {
            preloaded.forEach((key, img) -> {
                boolean[] mask = buildAlphaMask(img);
                if (mask != null) {
                    baseMasks.put(key, mask);
                }
            });
        }

        long tTint = VoxelBridgeLogger.now();
        List<AtlasRequest> requests = tintTasks.parallelStream()
            .map(task -> {
                BufferedImage base = preloaded.get(task.spriteKey());
                if (base == null) {
                    base = createMissingTexture();
                }
                BufferedImage tinted = tintTile(base, task.tint());
                BufferedImage output = padding > 0
                    ? applyPadding(tinted, padding, baseMasks.get(task.spriteKey()))
                    : tinted;
                return new AtlasRequest(task.spriteKey(), task.tintIndex(), output,
                    tinted.getWidth(), tinted.getHeight(), padding);
            })
            .collect(Collectors.toList());
        requests.sort(Comparator
            .comparing((AtlasRequest r) -> r.spriteKey)
            .thenComparingInt(r -> r.tintIndex));
        VoxelBridgeLogger.duration("atlas_tinting", VoxelBridgeLogger.elapsedSince(tTint));

        long tPack = VoxelBridgeLogger.now();
        int atlasSize = options.atlasSize();
        TextureAtlasPacker packer = new TextureAtlasPacker(atlasSize, false);
        int counter = 0;
        for (AtlasRequest req : requests) {
            String key = req.spriteKey + "#t" + req.tintIndex + "#" + counter++;
            requestByKey.put(key, req);
            packer.addTexture(key, req.image);
        }

        Path atlasDir = outDir.resolve(atlasDirName);
        Files.createDirectories(atlasDir);

        Map<String, TextureAtlasPacker.Placement> packed = packer.pack(atlasDir, atlasPrefix);
        for (Map.Entry<String, TextureAtlasPacker.Placement> entry : packed.entrySet()) {
            String key = entry.getKey();
            AtlasRequest req = requestByKey.get(key);
            if (req == null) {
                continue;
            }
            TextureAtlasPacker.Placement p = entry.getValue();

            int tileU = p.page() % 10;
            int tileV = p.page() / 10;
            int innerX = p.x() + req.pad();
            int innerY = p.y() + req.pad();
            int innerW = req.innerWidth();
            int innerH = req.innerHeight();
            double u0d = tileU + (double) innerX / atlasSize;
            double v0d = -tileV + (double) innerY / atlasSize;
            double u1d = tileU + (double) (innerX + innerW) / atlasSize;
            double v1d = -tileV + (double) (innerY + innerH) / atlasSize;
            float u0 = (float) u0d;
            float v0 = (float) v0d;
            float u1 = (float) u1d;
            float v1 = (float) v1d;

            ExportState.TintAtlas atlas = state.getAtlasBook().get(req.spriteKey);
            ExportState.TexturePlacement placement = new ExportState.TexturePlacement(
                p.page(), tileU, tileV, p.x(), p.y(), p.width(), p.height(),
                u0, v0, u1, v1, atlasDirName + "/" + atlasPrefix + p.udim() + ".png");
            atlas.placements.put(req.tintIndex, placement);
            atlas.usesAtlas = true;
            atlas.texW = atlasSize;
            atlas.texH = atlasSize;
            atlas.cols = 1;
            pagePathMap.putIfAbsent(p.page(), atlasDirName + "/" + atlasPrefix + p.udim() + ".png");
            pageToUdim.putIfAbsent(p.page(), p.udim());

            if (isBlockEntitySprite(req.spriteKey)) {
                int udim = p.udim();
                ExportState.BlockEntityAtlasPlacement bePlacement = new ExportState.BlockEntityAtlasPlacement(
                    p.page(), udim, innerX, innerY, innerW, innerH, atlasSize
                );
                state.getBlockEntityAtlasPlacements().put(req.spriteKey, bePlacement);
            }
        }

        for (Map.Entry<String, ExportState.TintAtlas> entry : entries.entrySet()) {
            ExportState.TintAtlas atlas = entry.getValue();
            ExportState.TexturePlacement placement = atlas.placements.getOrDefault(0,
                atlas.placements.values().stream().findFirst().orElse(null));
            if (placement != null) {
                String rel = placement.path() != null
                    ? placement.path()
                    : pagePathMap.getOrDefault(placement.page(), atlasDirName + "/" + atlasPrefix + "1001.png");
                materialPaths.put(entry.getKey(), rel);
            }
        }
        VoxelBridgeLogger.duration("atlas_packing_and_write", VoxelBridgeLogger.elapsedSince(tPack));

        long tPbr = VoxelBridgeLogger.now();
        generatePbrAtlases(options, state, outDir, entries, new HashSet<>(pageToUdim.keySet()),
            atlasSize, atlasDirName, atlasPrefix, pageToUdim, padding, baseMasks,
            normalImages, specImages, materialPaths, defaultNormalColor, defaultSpecColor, threadCount);
        VoxelBridgeLogger.duration("pbr_atlas_generation", VoxelBridgeLogger.elapsedSince(tPbr));
    }

    private static void generatePbrAtlases(ExportOptions options,
                                           ExportState state,
                                           Path outDir,
                                           Map<String, ExportState.TintAtlas> entries,
                                           Set<Integer> usedPages,
                                           int atlasSize,
                                           String atlasDirName,
                                           String basePrefix,
                                           Map<Integer, Integer> pageToUdim,
                                           int padding,
                                           Map<String, boolean[]> baseMasks,
                                           Map<String, BufferedImage> normalImages,
                                           Map<String, BufferedImage> specImages,
                                           Map<String, String> materialPaths,
                                           int defaultNormalColor,
                                           int defaultSpecColor,
                                           int threadCount) throws IOException {
        if (entries.isEmpty() || usedPages.isEmpty()) {
            return;
        }

        Path atlasDir = outDir.resolve(atlasDirName);

        Map<String, com.voxelbridge.core.texture.PbrAtlasWriter.Placement> flatPlacements = new LinkedHashMap<>();
        for (Map.Entry<String, ExportState.TintAtlas> entry : entries.entrySet()) {
            String spriteKey = entry.getKey();
            ExportState.TintAtlas atlas = entry.getValue();
            for (Map.Entry<Integer, ExportState.TexturePlacement> placementEntry : atlas.placements.entrySet()) {
                int tintIndex = placementEntry.getKey();
                ExportState.TexturePlacement placement = placementEntry.getValue();
                String key = tintIndex == 0 ? spriteKey : spriteKey + "#t" + tintIndex;
                flatPlacements.put(key, new PbrPlacementAdapters.TexturePlacementAdapter(placement));
            }
        }

        ExecutorService executor = Executors.newFixedThreadPool(Math.max(2, threadCount));
        try {
            Future<?> futureNormal = executor.submit(() -> {
                try {
                    Map<String, BufferedImage> paddedNormalCache = new ConcurrentHashMap<>();
                    com.voxelbridge.core.texture.PbrAtlasWriter.PbrAtlasConfig normalConfig =
                        new com.voxelbridge.core.texture.PbrAtlasWriter.PbrAtlasConfig(
                            atlasDir, atlasSize, basePrefix + "n_",
                            defaultNormalColor, usedPages, pageToUdim
                        );
                    com.voxelbridge.core.texture.PbrAtlasWriter.generatePbrAtlas(normalConfig, flatPlacements, key -> {
                        String spriteKey = key.contains("#") ? key.substring(0, key.indexOf("#")) : key;
                        BufferedImage normalImage = paddedNormalCache.computeIfAbsent(spriteKey, k -> {
                            BufferedImage img = normalImages.get(k);
                            if (img == null) {
                                return null;
                            }
                            if (padding > 0) {
                                return applyPadding(img, padding, baseMasks.get(k));
                            }
                            return img;
                        });
                        if (normalImage != null && !key.contains("#")) {
                            com.voxelbridge.core.texture.PbrAtlasWriter.Placement p = flatPlacements.get(key);
                            int udim = pageToUdim.getOrDefault(p.page(), p.page() + 1001);
                            String normalPath = atlasDirName + "/" + basePrefix + "n_" + udim + ".png";
                            materialPaths.put(spriteKey + "_n", normalPath);
                        }
                        return normalImage;
                    });
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });

            Future<?> futureSpec = executor.submit(() -> {
                try {
                    Map<String, BufferedImage> paddedSpecCache = new ConcurrentHashMap<>();
                    com.voxelbridge.core.texture.PbrAtlasWriter.PbrAtlasConfig specConfig =
                        new com.voxelbridge.core.texture.PbrAtlasWriter.PbrAtlasConfig(
                            atlasDir, atlasSize, basePrefix + "s_",
                            defaultSpecColor, usedPages, pageToUdim
                        );
                    com.voxelbridge.core.texture.PbrAtlasWriter.generatePbrAtlas(specConfig, flatPlacements, key -> {
                        String spriteKey = key.contains("#") ? key.substring(0, key.indexOf("#")) : key;
                        BufferedImage specImage = paddedSpecCache.computeIfAbsent(spriteKey, k -> {
                            BufferedImage img = specImages.get(k);
                            if (img == null) {
                                return null;
                            }
                            if (padding > 0) {
                                return applyPadding(img, padding, baseMasks.get(k));
                            }
                            return img;
                        });
                        if (specImage != null && !key.contains("#")) {
                            com.voxelbridge.core.texture.PbrAtlasWriter.Placement p = flatPlacements.get(key);
                            int udim = pageToUdim.getOrDefault(p.page(), p.page() + 1001);
                            String specPath = atlasDirName + "/" + basePrefix + "s_" + udim + ".png";
                            materialPaths.put(spriteKey + "_s", specPath);
                        }
                        return specImage;
                    });
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });

            futureNormal.get();
            futureSpec.get();
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof IOException io) {
                throw io;
            }
            throw new IOException("PBR atlas generation failed", cause);
        } finally {
            executor.shutdown();
        }
    }

    private static void ensureDefaultTint(ExportState.TintAtlas atlas) {
        if (atlas.tintToIndex.isEmpty()) {
            atlas.tintToIndex.put(DEFAULT_TINT, 0);
            atlas.indexToTint.put(0, DEFAULT_TINT);
            atlas.nextIndex.set(1);
        }
    }

    private static int[] resolveTintSlots(ExportState.TintAtlas atlas, int tintSlots) {
        int[] result = new int[tintSlots];
        boolean[] filled = new boolean[tintSlots];
        atlas.indexToTint.forEach((index, tint) -> {
            if (index >= 0 && index < tintSlots && !filled[index]) {
                result[index] = tint;
                filled[index] = true;
            }
        });
        if (tintSlots > 0 && !filled[0]) {
            result[0] = DEFAULT_TINT;
            filled[0] = true;
        }
        int last = tintSlots > 0 ? result[0] : DEFAULT_TINT;
        for (int i = 1; i < tintSlots; i++) {
            if (!filled[i]) {
                result[i] = last;
            } else {
                last = result[i];
            }
        }
        return result;
    }

    private static BufferedImage tintTile(BufferedImage tile, int tint) {
        int w = tile.getWidth();
        int h = tile.getHeight();

        BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        int[] srcData;
        int srcStride = w;
        int srcOffset = 0;
        var raster = tile.getRaster();
        if (raster.getDataBuffer() instanceof DataBufferInt db
            && raster.getSampleModel() instanceof SinglePixelPackedSampleModel sm) {
            srcData = db.getData();
            srcStride = sm.getScanlineStride();
            srcOffset = db.getOffset();
        } else {
            srcData = tile.getRGB(0, 0, w, h, null, 0, w);
        }
        int[] dstData = ((DataBufferInt) dst.getRaster().getDataBuffer()).getData();

        float rMul = ((tint >> 16) & 0xFF) / 255f;
        float gMul = ((tint >> 8) & 0xFF) / 255f;
        float bMul = (tint & 0xFF) / 255f;

        final int finalSrcStride = srcStride;
        final int finalSrcOffset = srcOffset;
        final int[] finalSrcData = srcData;

        java.util.stream.IntStream.range(0, h).parallel().forEach(y -> {
            int srcIdx = finalSrcOffset + y * finalSrcStride;
            int dstIdx = y * w;
            for (int x = 0; x < w; x++, srcIdx++, dstIdx++) {
                int argb = finalSrcData[srcIdx];
                if ((argb & 0xFF000000) == 0) {
                    continue;
                }
                int a = (argb >>> 24) & 0xFF;
                int r = (argb >>> 16) & 0xFF;
                int g = (argb >>> 8) & 0xFF;
                int b = argb & 0xFF;
                int rr = (int) (r * rMul);
                int gg = (int) (g * gMul);
                int bb = (int) (b * bMul);
                dstData[dstIdx] = (a << 24) | (rr << 16) | (gg << 8) | bb;
            }
        });

        return dst;
    }

    private static boolean[] buildAlphaMask(BufferedImage img) {
        if (img == null) {
            return null;
        }
        int w = img.getWidth();
        int h = img.getHeight();
        int[] data = img.getRGB(0, 0, w, h, null, 0, w);
        boolean[] mask = new boolean[w * h];
        for (int i = 0; i < data.length; i++) {
            if (((data[i] >>> 24) & 0xFF) != 0) {
                mask[i] = true;
            }
        }
        return mask;
    }

    private static BufferedImage applyPadding(BufferedImage src, int pad, boolean[] validMask) {
        if (src == null || pad <= 0) {
            return src;
        }
        int w = src.getWidth();
        int h = src.getHeight();

        final boolean[] finalValidMask = (validMask != null && validMask.length == w * h) ? validMask : null;

        int outW = w + pad * 2;
        int outH = h + pad * 2;

        BufferedImage dst = new BufferedImage(outW, outH, BufferedImage.TYPE_INT_ARGB);
        int[] srcData;
        int srcStride = w;
        int srcOffset = 0;
        var srcRaster = src.getRaster();
        if (srcRaster.getDataBuffer() instanceof DataBufferInt db
            && srcRaster.getSampleModel() instanceof SinglePixelPackedSampleModel sm) {
            srcData = db.getData();
            srcStride = sm.getScanlineStride();
            srcOffset = db.getOffset();
        } else {
            srcData = src.getRGB(0, 0, w, h, null, 0, w);
        }
        int[] dstData = ((DataBufferInt) dst.getRaster().getDataBuffer()).getData();
        boolean[] dstValid = new boolean[outW * outH];

        final int finalSrcOffset = srcOffset;
        final int finalSrcStride = srcStride;
        java.util.stream.IntStream.range(0, h).parallel().forEach(y -> {
            int srcRowStart = finalSrcOffset + y * finalSrcStride;
            int maskRowStart = y * w;
            int dstRowStart = (y + pad) * outW + pad;
            System.arraycopy(srcData, srcRowStart, dstData, dstRowStart, w);
            for (int x = 0; x < w; x++) {
                if (finalValidMask == null || finalValidMask[maskRowStart + x]) {
                    dstValid[dstRowStart + x] = true;
                }
            }
        });

        java.util.stream.IntStream.range(pad, pad + h).parallel().forEach(y -> {
            int rowStart = y * outW;
            int firstX = -1;
            int lastX = -1;

            for (int x = pad; x < pad + w; x++) {
                if (dstValid[rowStart + x]) {
                    if (firstX == -1) firstX = x;
                    lastX = x;
                }
            }

            if (firstX != -1) {
                int leftColor = dstData[rowStart + firstX];
                int rightColor = dstData[rowStart + lastX];

                if (pad > 0) {
                    java.util.Arrays.fill(dstData, rowStart, rowStart + pad, leftColor);
                    for (int k = 0; k < pad; k++) dstValid[rowStart + k] = true;
                }

                if (outW > pad + w) {
                    java.util.Arrays.fill(dstData, rowStart + pad + w, rowStart + outW, rightColor);
                    for (int k = pad + w; k < outW; k++) dstValid[rowStart + k] = true;
                }
            }
        });

        java.util.stream.IntStream.range(0, outW).parallel().forEach(x -> {
            int firstY = -1;
            int lastY = -1;

            for (int y = pad; y < pad + h; y++) {
                if (dstValid[y * outW + x]) {
                    if (firstY == -1) firstY = y;
                    lastY = y;
                }
            }

            if (firstY != -1) {
                int topColor = dstData[firstY * outW + x];
                int bottomColor = dstData[lastY * outW + x];

                for (int y = 0; y < pad; y++) {
                    dstData[y * outW + x] = topColor;
                }
                for (int y = pad + h; y < outH; y++) {
                    dstData[y * outW + x] = bottomColor;
                }
            }
        });

        return dst;
    }

    public static BufferedImage createMissingTexture() {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                boolean pink = ((x / 4) + (y / 4)) % 2 == 0;
                img.setRGB(x, y, pink ? 0xFFFF00FF : 0xFF000000);
            }
        }
        return img;
    }

    private static boolean isBlockEntitySprite(String spriteKey) {
        return spriteKey != null && (spriteKey.startsWith("blockentity:") || spriteKey.startsWith("entity:"));
    }
}
