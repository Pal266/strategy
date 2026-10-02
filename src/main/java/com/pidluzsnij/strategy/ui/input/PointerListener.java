package com.pidluzsnij.strategy.ui.input;

/**
 * Receives application-owned pointer input from the window system. Coordinates are framebuffer pixels with
 * the origin at the framebuffer's top-left corner; the framebuffer size at the time of the event accompanies
 * each event.
 */
public interface PointerListener {

    /** Ignores all pointer input. */
    PointerListener NONE = new PointerListener() {
        @Override
        public void pointerMoved(double x, double y, int framebufferWidth, int framebufferHeight) {
        }

        @Override
        public void primaryButton(boolean pressed, double x, double y, int framebufferWidth, int framebufferHeight) {
        }

        @Override
        public void pointerLeft() {
        }
    };

    /** The pointer moved to {@code (x, y)}. */
    void pointerMoved(double x, double y, int framebufferWidth, int framebufferHeight);

    /** The primary button was pressed or released with the pointer at {@code (x, y)}. */
    void primaryButton(boolean pressed, double x, double y, int framebufferWidth, int framebufferHeight);

    /** The pointer left the window. */
    void pointerLeft();
}
