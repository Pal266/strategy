package com.pidluzsnij.strategy.window;

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
    void renderBlackFrame();

    /** Processes pending window-system events. */
    void processEvents();

    boolean isCloseRequested();

    WindowState windowState();

    /** Cleanup: destroys the window and its graphics context if created. */
    void destroyWindow();

    /** Cleanup: terminates GLFW and releases the error callback if initialized. */
    void terminate();
}
