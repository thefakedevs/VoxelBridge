package com.voxelbridge.export.texture;

import ar.com.hjg.pngj.ImageInfo;
import ar.com.hjg.pngj.ImageLineInt;
import ar.com.hjg.pngj.PngReader;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.awt.image.SinglePixelPackedSampleModel;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Exports decoded LabPBR channel maps from cached _n/_s textures.
 * Mirrors tools/mat.py behavior.
 */
public final class LabPbrDecoder {

    private LabPbrDecoder() {}

    public static void exportDecoded(Path outDir, ExportOptions options, int threadCount, java.util.function.Consumer<Float> progressCallback) throws IOException {
        if (options == null) {
            return;
        }

        java.util.List<DecodeJob> jobs = new ArrayList<>();

        // Atlas decode (atlas mode only)
        Path atlasDir = outDir.resolve("textures").resolve("atlas");
        if (options.atlasMode() == ExportOptions.AtlasMode.ATLAS && Files.isDirectory(atlasDir)) {
            Map<String, Path> albedoPages = new HashMap<>();
            Map<String, Path> normalPages = new HashMap<>();
            Map<String, Path> specPages = new HashMap<>();

            try (var stream = Files.newDirectoryStream(atlasDir, "*.png")) {
                for (Path p : stream) {
                    String name = p.getFileName().toString();
                    if (name.startsWith("atlas_n_") && name.endsWith(".png")) {
                        String udim = name.substring("atlas_n_".length(), name.length() - 4);
                        normalPages.put(udim, p);
                    } else if (name.startsWith("atlas_s_") && name.endsWith(".png")) {
                        String udim = name.substring("atlas_s_".length(), name.length() - 4);
                        specPages.put(udim, p);
                    } else if (name.startsWith("atlas_") && name.endsWith(".png")) {
                        String udim = name.substring("atlas_".length(), name.length() - 4);
                        albedoPages.put(udim, p);
                    }
                }
            }

            Set<String> udimSet = new HashSet<>();
            udimSet.addAll(normalPages.keySet());
            udimSet.addAll(specPages.keySet());

            for (String udim : udimSet) {
                jobs.add(DecodeJob.atlas(atlasDir, udim,
                    albedoPages.get(udim), normalPages.get(udim), specPages.get(udim)));
            }
        }

        // Individual + animation decode (scan output dirs for *_n/_s)
        java.util.Map<Path, DecodeJob> standardJobs = new HashMap<>();
        collectStandardJobs(standardJobs, outDir.resolve("textures"), true);
        collectStandardJobs(standardJobs, outDir.resolve("entity_textures"), false);
        jobs.addAll(standardJobs.values());

        int totalTasks = 0;
        for (DecodeJob job : jobs) {
            if (job.normalPath != null) {
                totalTasks += 3; // normal, ao, height
            }
            if (job.specPath != null) {
                totalTasks += 4; // roughness, metallic, sss, emissive
            }
        }
        if (totalTasks == 0) {
            return;
        }

        final int totalTasksFinal = totalTasks;
        java.util.concurrent.atomic.AtomicInteger completed = new java.util.concurrent.atomic.AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(Math.max(2, threadCount));
        try {
            java.util.List<Future<?>> futures = new ArrayList<>(jobs.size());
            for (DecodeJob job : jobs) {
                futures.add(executor.submit(() -> {
                    try {
                        if (job.atlas) {
                            decodeAtlasJob(job, progressCallback, completed, totalTasksFinal);
                        } else {
                            decodeStandardJob(job, progressCallback, completed, totalTasksFinal);
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }));
            }
            for (Future<?> f : futures) {
                f.get();
            }
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof IOException io) {
                throw io;
            }
            throw new IOException("LabPBR decode failed", cause);
        } finally {
            executor.shutdown();
        }
    }

    private static void collectStandardJobs(java.util.Map<Path, DecodeJob> jobs, Path rootDir, boolean skipAtlasDir) throws IOException {
        if (rootDir == null || !Files.isDirectory(rootDir)) {
            return;
        }
        try (var stream = java.nio.file.Files.walk(rootDir)) {
            stream.filter(p -> p != null && p.getFileName() != null)
                .filter(p -> p.toString().endsWith(".png"))
                .forEach(p -> {
                    if (skipAtlasDir && (p.toString().contains("\\textures\\atlas\\") || p.toString().contains("/textures/atlas/"))) {
                        return;
                    }
                    String name = p.getFileName().toString();
                    boolean isNormal = name.endsWith("_n.png");
                    boolean isSpec = name.endsWith("_s.png");
                    if (!isNormal && !isSpec) {
                        return;
                    }
                    String baseName = name.substring(0, name.length() - 6);
                    Path basePath = p.getParent().resolve(baseName);
                    DecodeJob job = jobs.computeIfAbsent(basePath, DecodeJob::standard);
                    if (isNormal) {
                        job.normalPath = p;
                    } else if (isSpec) {
                        job.specPath = p;
                    }
                });
        }
        for (DecodeJob job : jobs.values()) {
            if (!job.atlas && job.albedoPath == null) {
                Path albedo = job.basePath.resolveSibling(job.basePath.getFileName().toString() + ".png");
                if (Files.exists(albedo)) {
                    job.albedoPath = albedo;
                }
            }
        }
    }

    private static void decodeAtlasJob(DecodeJob job,
                                       java.util.function.Consumer<Float> progressCallback,
                                       java.util.concurrent.atomic.AtomicInteger completed,
                                       int totalTasks) throws IOException {
        BufferedImage normal = readImage(job.normalPath);
        if (normal != null) {
            com.voxelbridge.core.texture.PngjWriter.write(decodeNormal(normal), job.atlasDir.resolve("atlas_normal_" + job.udim + ".png"));
            reportProgress(progressCallback, completed, totalTasks);
            com.voxelbridge.core.texture.PngjWriter.write(extractChannel(normal, Channel.BLUE), job.atlasDir.resolve("atlas_ao_" + job.udim + ".png"));
            reportProgress(progressCallback, completed, totalTasks);
            com.voxelbridge.core.texture.PngjWriter.write(extractChannel(normal, Channel.ALPHA), job.atlasDir.resolve("atlas_height_" + job.udim + ".png"));
            reportProgress(progressCallback, completed, totalTasks);
        }

        BufferedImage spec = readImage(job.specPath);
        if (spec != null) {
            BufferedImage albedo = readImage(job.albedoPath);
            if (albedo != null && (albedo.getWidth() != spec.getWidth() || albedo.getHeight() != spec.getHeight())) {
                albedo = resizeTo(albedo, spec.getWidth(), spec.getHeight());
            }
            com.voxelbridge.core.texture.PngjWriter.write(decodeRoughness(spec), job.atlasDir.resolve("atlas_roughness_" + job.udim + ".png"));
            reportProgress(progressCallback, completed, totalTasks);
            com.voxelbridge.core.texture.PngjWriter.write(decodeMetallic(spec), job.atlasDir.resolve("atlas_metallic_" + job.udim + ".png"));
            reportProgress(progressCallback, completed, totalTasks);
            com.voxelbridge.core.texture.PngjWriter.write(decodeSss(spec), job.atlasDir.resolve("atlas_sss_" + job.udim + ".png"));
            reportProgress(progressCallback, completed, totalTasks);
            com.voxelbridge.core.texture.PngjWriter.write(decodeEmissive(albedo, spec), job.atlasDir.resolve("atlas_emissive_" + job.udim + ".png"));
            reportProgress(progressCallback, completed, totalTasks);
        }
    }

    private static void decodeStandardJob(DecodeJob job,
                                          java.util.function.Consumer<Float> progressCallback,
                                          java.util.concurrent.atomic.AtomicInteger completed,
                                          int totalTasks) throws IOException {
        Path dir = job.basePath.getParent();
        String stem = job.basePath.getFileName().toString();
        if (job.normalPath != null) {
            BufferedImage normal = readImage(job.normalPath);
            if (normal != null) {
                com.voxelbridge.core.texture.PngjWriter.write(decodeNormal(normal), dir.resolve(stem + "_normal.png"));
                reportProgress(progressCallback, completed, totalTasks);
                com.voxelbridge.core.texture.PngjWriter.write(extractChannel(normal, Channel.BLUE), dir.resolve(stem + "_ao.png"));
                reportProgress(progressCallback, completed, totalTasks);
                com.voxelbridge.core.texture.PngjWriter.write(extractChannel(normal, Channel.ALPHA), dir.resolve(stem + "_height.png"));
                reportProgress(progressCallback, completed, totalTasks);
            }
        }

        if (job.specPath != null) {
            BufferedImage spec = readImage(job.specPath);
            if (spec != null) {
                BufferedImage albedo = readImage(job.albedoPath);
                if (albedo != null && (albedo.getWidth() != spec.getWidth() || albedo.getHeight() != spec.getHeight())) {
                    albedo = resizeTo(albedo, spec.getWidth(), spec.getHeight());
                }
                com.voxelbridge.core.texture.PngjWriter.write(decodeRoughness(spec), dir.resolve(stem + "_roughness.png"));
                reportProgress(progressCallback, completed, totalTasks);
                com.voxelbridge.core.texture.PngjWriter.write(decodeMetallic(spec), dir.resolve(stem + "_metallic.png"));
                reportProgress(progressCallback, completed, totalTasks);
                com.voxelbridge.core.texture.PngjWriter.write(decodeSss(spec), dir.resolve(stem + "_sss.png"));
                reportProgress(progressCallback, completed, totalTasks);
                com.voxelbridge.core.texture.PngjWriter.write(decodeEmissive(albedo, spec), dir.resolve(stem + "_emissive.png"));
                reportProgress(progressCallback, completed, totalTasks);
            }
        }
    }

    private static final class DecodeJob {
        final boolean atlas;
        final Path atlasDir;
        final String udim;
        final Path basePath;
        Path albedoPath;
        Path normalPath;
        Path specPath;

        private DecodeJob(boolean atlas, Path atlasDir, String udim, Path basePath) {
            this.atlas = atlas;
            this.atlasDir = atlasDir;
            this.udim = udim;
            this.basePath = basePath;
        }

        static DecodeJob atlas(Path atlasDir, String udim, Path albedo, Path normal, Path spec) {
            DecodeJob job = new DecodeJob(true, atlasDir, udim, null);
            job.albedoPath = albedo;
            job.normalPath = normal;
            job.specPath = spec;
            return job;
        }

        static DecodeJob standard(Path basePath) {
            return new DecodeJob(false, null, null, basePath);
        }
    }

    private static void reportProgress(java.util.function.Consumer<Float> progressCallback,
                                       java.util.concurrent.atomic.AtomicInteger completed,
                                       int totalTasks) {
        if (progressCallback == null) {
            return;
        }
        int done = completed.incrementAndGet();
        progressCallback.accept((float) done / totalTasks);
    }

    private static final int[] ROUGHNESS_LUT = new int[256];
    private static final int[] METALLIC_LUT = new int[256];
    private static final int[] SSS_LUT = new int[256];
    private static final double[] NORMAL_COMPONENT_LUT = new double[256];

    static {
        for (int i = 0; i < 256; i++) {
            // Roughness
            double smoothness = i / 255.0;
            double roughness = (1.0 - smoothness) * (1.0 - smoothness);
            ROUGHNESS_LUT[i] = clamp255(roughness * 255.0);

            // Metallic
            METALLIC_LUT[i] = i >= 230 ? 255 : 0;

            // SSS (precompute the mapping for 'b' when condition is met)
            double sss = 0.0;
            if (i >= 65) {
                sss = (i - 65.0) / 190.0;
            }
            SSS_LUT[i] = clamp255(sss * 255.0);

            // Normal component (-1.0 to 1.0)
            NORMAL_COMPONENT_LUT[i] = i / 255.0 * 2.0 - 1.0;
        }
    }

    private static BufferedImage decodeNormal(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        IntRaster src = getIntRaster(img);
        BufferedImage result = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        int[] out = ((DataBufferInt) result.getRaster().getDataBuffer()).getData();

        int outIdx = 0;
        int srcRowStart = src.offset;
        for (int y = 0; y < h; y++) {
            int srcIdx = srcRowStart;
            for (int x = 0; x < w; x++, srcIdx++, outIdx++) {
                int argb = src.data[srcIdx];
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                double nx = NORMAL_COMPONENT_LUT[r];
                double ny = NORMAL_COMPONENT_LUT[g];
                double nz2 = 1.0 - nx * nx - ny * ny;
                double nz = nz2 > 0.0 ? Math.sqrt(nz2) : 0.0;
                int nr = clamp255((nx + 1.0) * 0.5 * 255.0);
                int ng = clamp255((ny + 1.0) * 0.5 * 255.0);
                int nb = clamp255((nz + 1.0) * 0.5 * 255.0);
                out[outIdx] = (0xFF << 24) | (nr << 16) | (ng << 8) | nb;
            }
            srcRowStart += src.stride;
        }
        return result;
    }

    private static BufferedImage decodeRoughness(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        IntRaster src = getIntRaster(img);
        BufferedImage result = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        int[] out = ((DataBufferInt) result.getRaster().getDataBuffer()).getData();

        int outIdx = 0;
        int srcRowStart = src.offset;
        for (int y = 0; y < h; y++) {
            int srcIdx = srcRowStart;
            for (int x = 0; x < w; x++, srcIdx++, outIdx++) {
                int r = (src.data[srcIdx] >> 16) & 0xFF;
                int v = ROUGHNESS_LUT[r];
                out[outIdx] = (0xFF << 24) | (v << 16) | (v << 8) | v;
            }
            srcRowStart += src.stride;
        }
        return result;
    }

    private static BufferedImage decodeMetallic(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        IntRaster src = getIntRaster(img);
        BufferedImage result = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        int[] out = ((DataBufferInt) result.getRaster().getDataBuffer()).getData();

        int outIdx = 0;
        int srcRowStart = src.offset;
        for (int y = 0; y < h; y++) {
            int srcIdx = srcRowStart;
            for (int x = 0; x < w; x++, srcIdx++, outIdx++) {
                int g = (src.data[srcIdx] >> 8) & 0xFF;
                int v = METALLIC_LUT[g];
                out[outIdx] = (0xFF << 24) | (v << 16) | (v << 8) | v;
            }
            srcRowStart += src.stride;
        }
        return result;
    }

    private static BufferedImage decodeEmissive(BufferedImage albedo, BufferedImage spec) {
        int w = spec.getWidth();
        int h = spec.getHeight();
        IntRaster src = getIntRaster(spec);
        int[] base = null;
        IntRaster baseRaster = null;
        if (albedo != null) {
            baseRaster = getIntRaster(albedo);
            base = baseRaster.data;
        }

        BufferedImage result = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        int[] out = ((DataBufferInt) result.getRaster().getDataBuffer()).getData();

        int outIdx = 0;
        int srcRowStart = src.offset;
        int baseRowStart = baseRaster != null ? baseRaster.offset : 0;
        for (int y = 0; y < h; y++) {
            int srcIdx = srcRowStart;
            int baseIdx = baseRowStart;
            for (int x = 0; x < w; x++, srcIdx++, baseIdx++, outIdx++) {
                int a = (src.data[srcIdx] >>> 24) & 0xFF;
                int strength = a == 255 ? 0 : a;
                double s = strength / 254.0;
                if (base != null) {
                    int br = (base[baseIdx] >> 16) & 0xFF;
                    int bg = (base[baseIdx] >> 8) & 0xFF;
                    int bb = base[baseIdx] & 0xFF;
                    int r = clamp255(br * s);
                    int g = clamp255(bg * s);
                    int b = clamp255(bb * s);
                    out[outIdx] = (0xFF << 24) | (r << 16) | (g << 8) | b;
                } else {
                    int v = clamp255(s * 255.0);
                    out[outIdx] = (0xFF << 24) | (v << 16) | (v << 8) | v;
                }
            }
            srcRowStart += src.stride;
            if (baseRaster != null) {
                baseRowStart += baseRaster.stride;
            }
        }
        return result;
    }

    private static BufferedImage decodeSss(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        IntRaster src = getIntRaster(img);
        BufferedImage result = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        int[] out = ((DataBufferInt) result.getRaster().getDataBuffer()).getData();

        int outIdx = 0;
        int srcRowStart = src.offset;
        for (int y = 0; y < h; y++) {
            int srcIdx = srcRowStart;
            for (int x = 0; x < w; x++, srcIdx++, outIdx++) {
                int g = (src.data[srcIdx] >> 8) & 0xFF;
                int b = src.data[srcIdx] & 0xFF;
                int v = (g < 230) ? SSS_LUT[b] : 0;
                out[outIdx] = (0xFF << 24) | (v << 16) | (v << 8) | v;
            }
            srcRowStart += src.stride;
        }
        return result;
    }

    private static BufferedImage extractChannel(BufferedImage img, Channel channel) {
        int w = img.getWidth();
        int h = img.getHeight();
        IntRaster src = getIntRaster(img);
        BufferedImage result = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        int[] out = ((DataBufferInt) result.getRaster().getDataBuffer()).getData();

        int outIdx = 0;
        int srcRowStart = src.offset;
        for (int y = 0; y < h; y++) {
            int srcIdx = srcRowStart;
            for (int x = 0; x < w; x++, srcIdx++, outIdx++) {
                int argb = src.data[srcIdx];
                int v = switch (channel) {
                    case RED -> (argb >> 16) & 0xFF;
                    case GREEN -> (argb >> 8) & 0xFF;
                    case BLUE -> argb & 0xFF;
                    case ALPHA -> (argb >>> 24) & 0xFF;
                };
                out[outIdx] = (0xFF << 24) | (v << 16) | (v << 8) | v;
            }
            srcRowStart += src.stride;
        }
        return result;
    }

    private static BufferedImage resizeTo(BufferedImage src, int w, int h) {
        BufferedImage scaled = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return scaled;
    }

    private static int clamp255(double v) {
        if (v <= 0.0) return 0;
        if (v >= 255.0) return 255;
        return (int) v;
    }

    private static BufferedImage readImage(Path path) throws IOException {
        if (path == null || !Files.exists(path)) {
            return null;
        }
        PngReader reader = new PngReader(path.toFile());
        try {
            ImageInfo info = reader.imgInfo;
            int w = info.cols;
            int h = info.rows;
            int channels = info.channels;
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            int[] out = ((DataBufferInt) img.getRaster().getDataBuffer()).getData();
            for (int y = 0; y < h; y++) {
                ImageLineInt line = (ImageLineInt) reader.readRow();
                int[] scan = line.getScanline();
                int rowOff = y * w;
                int idx = 0;
                for (int x = 0; x < w; x++) {
                    int r;
                    int g;
                    int b;
                    int a;
                    if (channels == 4) {
                        r = scan[idx++];
                        g = scan[idx++];
                        b = scan[idx++];
                        a = scan[idx++];
                    } else if (channels == 3) {
                        r = scan[idx++];
                        g = scan[idx++];
                        b = scan[idx++];
                        a = 255;
                    } else if (channels == 2) {
                        r = scan[idx++];
                        g = r;
                        b = r;
                        a = scan[idx++];
                    } else {
                        r = scan[idx++];
                        g = r;
                        b = r;
                        a = 255;
                    }
                    if (info.bitDepth == 16) {
                        r = r >> 8;
                        g = g >> 8;
                        b = b >> 8;
                        a = a >> 8;
                    }
                    out[rowOff + x] = (a << 24) | (r << 16) | (g << 8) | b;
                }
            }
            return img;
        } finally {
            reader.close();
        }
    }

    private static IntRaster getIntRaster(BufferedImage img) {
        var raster = img.getRaster();
        if (raster.getDataBuffer() instanceof DataBufferInt db
            && raster.getSampleModel() instanceof SinglePixelPackedSampleModel sm) {
            return new IntRaster(db.getData(), sm.getScanlineStride(), db.getOffset());
        }
        int w = img.getWidth();
        int h = img.getHeight();
        int[] data = img.getRGB(0, 0, w, h, null, 0, w);
        return new IntRaster(data, w, 0);
    }

    private record IntRaster(int[] data, int stride, int offset) {}

    private enum Channel {
        RED, GREEN, BLUE, ALPHA
    }
}
