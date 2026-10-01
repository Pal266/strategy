package com.pidluzsnij.strategy.window;

/** Application resolution in pixels. */
public record Resolution(int width, int height) {

    /** Used when the starting monitor's resolution cannot be determined. */
    public static final Resolution FALLBACK = new Resolution(1280, 720);

    @Override
    public String toString() {
        return width + "×" + height;
    }
}
