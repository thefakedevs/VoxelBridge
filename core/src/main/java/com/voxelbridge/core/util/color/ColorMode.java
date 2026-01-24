package com.voxelbridge.core.util.color;

/**
 * Export color handling mode.
 */
public enum ColorMode {
    VERTEX_COLOR("Vertex Color (COLOR_0 attribute)"),
    COLORMAP("ColorMap (TEXCOORD_1 + texture)"),
    BOTH("Both (COLOR_0 + TEXCOORD_1 colormap)");

    private final String description;

    ColorMode(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public boolean usesColormap() {
        return this == COLORMAP || this == BOTH;
    }

    public boolean usesVertexColor() {
        return this == VERTEX_COLOR || this == BOTH;
    }
}
