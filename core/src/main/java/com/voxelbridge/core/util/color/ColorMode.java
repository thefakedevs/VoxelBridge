package com.voxelbridge.core.util.color;

/**
 * Export color handling mode.
 */
public enum ColorMode {
    VERTEX_COLOR("Vertex Color (COLOR_0 attribute)"),
    COLORMAP("ColorMap (TEXCOORD_1 + texture)");

    private final String description;

    ColorMode(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
