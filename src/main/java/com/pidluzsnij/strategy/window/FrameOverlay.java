package com.pidluzsnij.strategy.window;

/** Content drawn over the black frame before it is presented. */
@FunctionalInterface
public interface FrameOverlay {

    /** Draws nothing. */
    FrameOverlay NONE = (framebufferWidth, framebufferHeight) -> { };

    void draw(int framebufferWidth, int framebufferHeight);
}
