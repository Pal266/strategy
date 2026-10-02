package com.pidluzsnij.strategy.ui.asset;

import java.util.Objects;

/**
 * A decoded image as straight-alpha RGBA8 pixels, rows from top to bottom. Pixel data is held on the Java
 * heap, so the image owns no native memory.
 */
public final class UiImage {

    private final int width;
    private final int height;
    private final byte[] rgba;

    public UiImage(int width, int height, byte[] rgba) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("image dimensions must be positive");
        }
        Objects.requireNonNull(rgba, "rgba");
        if ((long) width * height * 4 != rgba.length) {
            throw new IllegalArgumentException("pixel data does not match the image dimensions");
        }
        this.width = width;
        this.height = height;
        this.rgba = rgba;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    /** @return a copy of the RGBA8 pixel data */
    public byte[] rgba() {
        return rgba.clone();
    }


    /** @return the pixel at {@code (x, y)} as {@code 0xRRGGBBAA} */
    public int pixel(int x, int y) {
        int i = (y * width + x) * 4;
        return (rgba[i] & 0xFF) << 24 | (rgba[i + 1] & 0xFF) << 16 | (rgba[i + 2] & 0xFF) << 8 | (rgba[i + 3] & 0xFF);
    }
}
