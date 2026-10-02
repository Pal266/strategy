package com.pidluzsnij.strategy.ui.layout;

/** A rectangle in framebuffer pixels; the origin is the framebuffer's top-left corner. */
public record ScreenRect(float x, float y, float width, float height) {

    /**
     * Hit test with the left and top edges inside and the right and bottom edges outside, so adjacent
     * rectangles never both contain a point.
     */
    public boolean contains(double px, double py) {
        return px >= x && py >= y && px < x + width && py < y + height;
    }
}
