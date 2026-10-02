package com.pidluzsnij.strategy.window;

import com.pidluzsnij.strategy.ui.input.PointerListener;
import com.pidluzsnij.strategy.ui.render.UiGraphics;

/**
 * Windowing and graphics backend. Methods are called from the main thread in the
 * order used by {@link Application}.
 */
public interface WindowSystem {

    /** GLFW initialization stage. Installs the error listener before initializing. */
    void initialize(GlfwErrorListener errorListener);

    /** Describes the monitor the application starts on. */
    MonitorInfo startingMonitor();

    /** Window creation stage. Fullscreen windows use the starting monitor. */
    void createWindow(WindowSettings settings, Resolution resolution);

    /** OpenGL initialization stage. Makes the context current and describes it. */
    GraphicsInfo initializeGraphics();

    /** Clears the window to black and presents it. */
    default void renderBlackFrame() {
        renderFrame(FrameOverlay.NONE);
    }

    /** Clears the window to black, lets {@code overlay} draw over it, and presents it. */
    void renderFrame(FrameOverlay overlay);

    /** @return the current framebuffer size in pixels */
    Resolution framebufferSize();

    /** @return the UI drawing backend for the window's graphics context; the context must be current */
    UiGraphics createUiGraphics();

    /** Delivers the window's pointer input to {@code listener} during {@link #processEvents()}. */
    void setPointerListener(PointerListener listener);

    /** Processes pending window-system events. */
    void processEvents();

    boolean isCloseRequested();

    WindowState windowState();

    /** Cleanup: destroys the window and its graphics context if created. */
    void destroyWindow();

    /** Cleanup: terminates GLFW and releases the error callback if initialized. */
    void terminate();
}
