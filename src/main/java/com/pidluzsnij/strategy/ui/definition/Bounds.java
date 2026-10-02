package com.pidluzsnij.strategy.ui.definition;

/** A component's rectangle in the definition's logical coordinate space; the origin is the top-left corner. */
public record Bounds(int x, int y, int width, int height) {

    public Bounds {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("bounds must have a positive width and height");
        }
    }
}
