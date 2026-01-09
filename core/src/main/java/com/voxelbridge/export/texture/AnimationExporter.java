package com.voxelbridge.export.texture;

import com.voxelbridge.core.texture.AnimatedFrameSet;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes animation frames and optional mcmeta to disk.
 */
public final class AnimationExporter {

    private AnimationExporter() {}

    public static void exportAnimation(Path baseDir,
                                       String baseName,
                                       AnimatedFrameSet frames,
                                       AnimatedFrameSet normalFrames,
                                       AnimatedFrameSet specFrames,
                                       String mcmetaContent) throws IOException {
        if (frames == null || frames.isEmpty()) {
            return;
        }
        Path spriteDir = baseDir.resolve(baseName);
        Files.createDirectories(spriteDir);

        int frameCount = frames.frames().size();
        for (int i = 0; i < frameCount; i++) {
            String idx = String.format("%03d", i);
            Path framePath = spriteDir.resolve(baseName + "_" + idx + ".png");
            ImageIO.write(frames.frames().get(i), "PNG", framePath.toFile());
        }

        if (normalFrames != null && !normalFrames.isEmpty()) {
            for (int i = 0; i < frameCount; i++) {
                String idx = String.format("%03d", i);
                int normalIdx = i % normalFrames.frames().size();
                Path framePath = spriteDir.resolve(baseName + "_" + idx + "_n.png");
                ImageIO.write(normalFrames.frames().get(normalIdx), "PNG", framePath.toFile());
            }
        }

        if (specFrames != null && !specFrames.isEmpty()) {
            for (int i = 0; i < frameCount; i++) {
                String idx = String.format("%03d", i);
                int specIdx = i % specFrames.frames().size();
                Path framePath = spriteDir.resolve(baseName + "_" + idx + "_s.png");
                ImageIO.write(specFrames.frames().get(specIdx), "PNG", framePath.toFile());
            }
        }

        if (mcmetaContent != null && !mcmetaContent.isEmpty()) {
            Path metaPath = spriteDir.resolve(baseName + ".mcmeta");
            Files.writeString(metaPath, mcmetaContent, StandardCharsets.UTF_8);
        }
    }
}
