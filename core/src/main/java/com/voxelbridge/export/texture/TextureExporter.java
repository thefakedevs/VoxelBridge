package com.voxelbridge.export.texture;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Core PNG writer for texture exports.
 */
public final class TextureExporter {

    private TextureExporter() {}

    public static void writePng(BufferedImage image, Path target) throws IOException {
        if (image == null || target == null) {
            return;
        }
        Files.createDirectories(target.getParent());
        ImageIO.write(image, "PNG", target.toFile());
    }
}
