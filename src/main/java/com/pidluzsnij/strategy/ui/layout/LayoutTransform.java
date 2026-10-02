package com.pidluzsnij.strategy.ui.layout;

import com.pidluzsnij.strategy.ui.definition.Bounds;

/**
 * Maps a definition's logical coordinate space to the framebuffer: the logical area is scaled uniformly to
 * fit the framebuffer and centered, so its authored aspect ratio is preserved and any unused framebuffer area
 * lies outside it. Rendering and pointer hit testing both use this one transform.
 */
public record LayoutTransform(int logicalWidth, int logicalHeight, int framebufferWidth, int framebufferHeight,
                              float scale, float offsetX, float offsetY) {

    /** @return the transform fitting a {@code logicalWidth × logicalHeight} area into the framebuffer */
    public static LayoutTransform fit(int logicalWidth, int logicalHeight, int framebufferWidth, int framebufferHeight) {
        if (logicalWidth <= 0 || logicalHeight <= 0) {
            throw new IllegalArgumentException("the logical size must be positive");
        }
        if (framebufferWidth <= 0 || framebufferHeight <= 0) {
            // A minimized window has an empty framebuffer: nothing is visible or hittable.
            return new LayoutTransform(logicalWidth, logicalHeight, Math.max(framebufferWidth, 0),
                    Math.max(framebufferHeight, 0), 0f, 0f, 0f);
        }
        float scale = Math.min((float) framebufferWidth / logicalWidth, (float) framebufferHeight / logicalHeight);
        float offsetX = (framebufferWidth - logicalWidth * scale) / 2f;
        float offsetY = (framebufferHeight - logicalHeight * scale) / 2f;
        return new LayoutTransform(logicalWidth, logicalHeight, framebufferWidth, framebufferHeight,
                scale, offsetX, offsetY);
    }

    /** @return the logical UI area in framebuffer pixels */
    public ScreenRect logicalArea() {
        return new ScreenRect(offsetX, offsetY, logicalWidth * scale, logicalHeight * scale);
    }

    /** @return {@code bounds} in framebuffer pixels */
    public ScreenRect toFramebuffer(Bounds bounds) {
        return new ScreenRect(offsetX + bounds.x() * scale, offsetY + bounds.y() * scale,
                bounds.width() * scale, bounds.height() * scale);
    }

    /**
     * @return whether the framebuffer point {@code (px, py)} lies inside both the logical UI area and the
     * rendered rectangle of {@code bounds}
     */
    public boolean hits(Bounds bounds, double px, double py) {
        return logicalArea().contains(px, py) && toFramebuffer(bounds).contains(px, py);
    }
}
