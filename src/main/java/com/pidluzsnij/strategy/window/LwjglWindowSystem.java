package com.pidluzsnij.strategy.window;

import com.pidluzsnij.strategy.ui.input.PointerListener;
import com.pidluzsnij.strategy.ui.render.UiGraphics;
import com.pidluzsnij.strategy.ui.render.gl.GlUiGraphics;
import org.lwjgl.glfw.Callbacks;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.Callback;
import org.lwjgl.system.Configuration;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.Platform;

import java.nio.IntBuffer;

import static org.lwjgl.glfw.GLFW.GLFW_FALSE;
import static org.lwjgl.glfw.GLFW.GLFW_ICONIFIED;
import static org.lwjgl.glfw.GLFW.GLFW_REFRESH_RATE;
import static org.lwjgl.glfw.GLFW.GLFW_RESIZABLE;
import static org.lwjgl.glfw.GLFW.GLFW_TRUE;
import static org.lwjgl.glfw.GLFW.GLFW_VISIBLE;
import static org.lwjgl.glfw.GLFW.glfwCreateWindow;
import static org.lwjgl.glfw.GLFW.glfwDefaultWindowHints;
import static org.lwjgl.glfw.GLFW.glfwDestroyWindow;
import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT;
import static org.lwjgl.glfw.GLFW.GLFW_PRESS;
import static org.lwjgl.glfw.GLFW.GLFW_RELEASE;
import static org.lwjgl.glfw.GLFW.glfwGetCursorPos;
import static org.lwjgl.glfw.GLFW.glfwGetFramebufferSize;
import static org.lwjgl.glfw.GLFW.glfwGetMonitorName;
import static org.lwjgl.glfw.GLFW.glfwGetPrimaryMonitor;
import static org.lwjgl.glfw.GLFW.glfwGetVideoMode;
import static org.lwjgl.glfw.GLFW.glfwGetWindowAttrib;
import static org.lwjgl.glfw.GLFW.glfwGetWindowMonitor;
import static org.lwjgl.glfw.GLFW.glfwGetWindowSize;
import static org.lwjgl.glfw.GLFW.glfwInit;
import static org.lwjgl.glfw.GLFW.glfwMakeContextCurrent;
import static org.lwjgl.glfw.GLFW.glfwPollEvents;
import static org.lwjgl.glfw.GLFW.glfwSetCursorEnterCallback;
import static org.lwjgl.glfw.GLFW.glfwSetCursorPosCallback;
import static org.lwjgl.glfw.GLFW.glfwSetErrorCallback;
import static org.lwjgl.glfw.GLFW.glfwSetMouseButtonCallback;
import static org.lwjgl.glfw.GLFW.glfwSetWindowShouldClose;
import static org.lwjgl.glfw.GLFW.glfwSwapBuffers;
import static org.lwjgl.glfw.GLFW.glfwSwapInterval;
import static org.lwjgl.glfw.GLFW.glfwTerminate;
import static org.lwjgl.glfw.GLFW.glfwWaitEvents;
import static org.lwjgl.glfw.GLFW.glfwWindowHint;
import static org.lwjgl.glfw.GLFW.glfwWindowShouldClose;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_RENDERER;
import static org.lwjgl.opengl.GL11.GL_VENDOR;
import static org.lwjgl.opengl.GL11.GL_VERSION;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glClearColor;
import static org.lwjgl.opengl.GL11.glGetString;
import static org.lwjgl.system.MemoryUtil.NULL;

/** {@link WindowSystem} backed by LWJGL's GLFW and OpenGL bindings. */
public final class LwjglWindowSystem implements WindowSystem {

    /**
     * GLFW variant for macOS that dispatches to the main thread itself, so the JVM does not
     * need {@code -XstartOnFirstThread} (an option other platforms' JVMs reject).
     */
    static final String MACOS_GLFW_LIBRARY_NAME = "glfw_async";

    private GLFWErrorCallback errorCallback;
    private boolean initialized;
    private long monitor = NULL;
    private VideoMode monitorVideoMode;
    private long window = NULL;
    private PointerListener pointerListener = PointerListener.NONE;
    private boolean pointerCallbacksInstalled;

    @Override
    public void initialize(GlfwErrorListener errorListener) {
        selectGlfwLibrary(Platform.get());
        errorCallback = GLFWErrorCallback.create((code, description) ->
                errorListener.onError(code, GLFWErrorCallback.getDescription(description)));
        glfwSetErrorCallback(errorCallback);
        if (!glfwInit()) {
            throw new IllegalStateException("glfwInit returned false");
        }
        initialized = true;
    }

    /**
     * Chooses the GLFW native library before GLFW is first loaded. An explicitly configured
     * library name is left unchanged.
     */
    static void selectGlfwLibrary(Platform platform) {
        String library = glfwLibraryNameFor(platform);
        if (library != null && Configuration.GLFW_LIBRARY_NAME.get() == null) {
            Configuration.GLFW_LIBRARY_NAME.set(library);
        }
    }

    /** @return the GLFW library name to use on the platform, or {@code null} for LWJGL's default */
    static String glfwLibraryNameFor(Platform platform) {
        return platform == Platform.MACOSX ? MACOS_GLFW_LIBRARY_NAME : null;
    }

    @Override
    public MonitorInfo startingMonitor() {
        monitor = glfwGetPrimaryMonitor();
        if (monitor == NULL) {
            return MonitorInfo.unavailable("GLFW reported no monitor");
        }
        String name = glfwGetMonitorName(monitor);
        GLFWVidMode mode = glfwGetVideoMode(monitor);
        if (mode == null) {
            return new MonitorInfo(name, null, "GLFW reported no current video mode for the monitor");
        }
        monitorVideoMode = new VideoMode(mode.width(), mode.height(), mode.refreshRate());
        return new MonitorInfo(name, monitorVideoMode, null);
    }

    @Override
    public void createWindow(WindowSettings settings, Resolution resolution) {
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_TRUE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_FALSE);
        boolean useMonitor = settings.fullscreen() && monitor != NULL;
        if (useMonitor && monitorVideoMode != null
                && monitorVideoMode.width() == resolution.width()
                && monitorVideoMode.height() == resolution.height()) {
            glfwWindowHint(GLFW_REFRESH_RATE, monitorVideoMode.refreshRate());
        }
        window = glfwCreateWindow(resolution.width(), resolution.height(), settings.title(),
                useMonitor ? monitor : NULL, NULL);
        if (window == NULL) {
            throw new IllegalStateException("glfwCreateWindow returned NULL");
        }
    }

    @Override
    public GraphicsInfo initializeGraphics() {
        glfwMakeContextCurrent(window);
        GL.createCapabilities();
        glfwSwapInterval(1);
        return new GraphicsInfo(glGetString(GL_VERSION), glGetString(GL_VENDOR), glGetString(GL_RENDERER));
    }

    @Override
    public void renderFrame(FrameOverlay overlay) {
        glClearColor(0f, 0f, 0f, 1f);
        glClear(GL_COLOR_BUFFER_BIT);
        if (overlay != FrameOverlay.NONE) {
            Resolution framebuffer = framebufferSize();
            overlay.draw(framebuffer.width(), framebuffer.height());
        }
        glfwSwapBuffers(window);
    }

    @Override
    public Resolution framebufferSize() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer width = stack.mallocInt(1);
            IntBuffer height = stack.mallocInt(1);
            glfwGetFramebufferSize(window, width, height);
            return new Resolution(width.get(0), height.get(0));
        }
    }

    @Override
    public UiGraphics createUiGraphics() {
        return new GlUiGraphics();
    }

    /**
     * Installs GLFW cursor and mouse-button callbacks; they are freed with the window. Cursor positions are
     * converted from window coordinates to framebuffer pixels.
     */
    @Override
    public void setPointerListener(PointerListener listener) {
        if (window == NULL) {
            throw new IllegalStateException("the window has not been created");
        }
        pointerListener = listener == null ? PointerListener.NONE : listener;
        if (pointerCallbacksInstalled) {
            // The callbacks read the current listener, so replacing the listener is enough.
            return;
        }
        freeCallback(glfwSetCursorPosCallback(window, (handle, x, y) -> {
            double[] point = toFramebuffer(x, y);
            pointerListener.pointerMoved(point[0], point[1], (int) point[2], (int) point[3]);
        }));
        freeCallback(glfwSetMouseButtonCallback(window, (handle, button, action, mods) -> {
            if (button != GLFW_MOUSE_BUTTON_LEFT || (action != GLFW_PRESS && action != GLFW_RELEASE)) {
                return;
            }
            double[] point = cursorInFramebuffer();
            pointerListener.primaryButton(action == GLFW_PRESS, point[0], point[1], (int) point[2], (int) point[3]);
        }));
        freeCallback(glfwSetCursorEnterCallback(window, (handle, entered) -> {
            if (!entered) {
                pointerListener.pointerLeft();
            }
        }));
        pointerCallbacksInstalled = true;
    }

    /** Frees a callback that a {@code glfwSet*Callback} call replaced. */
    private static void freeCallback(Callback previous) {
        if (previous != null) {
            previous.free();
        }
    }

    private double[] cursorInFramebuffer() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            java.nio.DoubleBuffer x = stack.mallocDouble(1);
            java.nio.DoubleBuffer y = stack.mallocDouble(1);
            glfwGetCursorPos(window, x, y);
            return toFramebuffer(x.get(0), y.get(0));
        }
    }

    /** @return {x, y, framebuffer width, framebuffer height} for a cursor position in window coordinates */
    private double[] toFramebuffer(double x, double y) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer windowWidth = stack.mallocInt(1);
            IntBuffer windowHeight = stack.mallocInt(1);
            IntBuffer framebufferWidth = stack.mallocInt(1);
            IntBuffer framebufferHeight = stack.mallocInt(1);
            glfwGetWindowSize(window, windowWidth, windowHeight);
            glfwGetFramebufferSize(window, framebufferWidth, framebufferHeight);
            double scaleX = windowWidth.get(0) > 0 ? (double) framebufferWidth.get(0) / windowWidth.get(0) : 1.0;
            double scaleY = windowHeight.get(0) > 0 ? (double) framebufferHeight.get(0) / windowHeight.get(0) : 1.0;
            return new double[] {x * scaleX, y * scaleY, framebufferWidth.get(0), framebufferHeight.get(0)};
        }
    }

    @Override
    public void processEvents() {
        if (glfwGetWindowAttrib(window, GLFW_ICONIFIED) == GLFW_TRUE) {
            // Nothing is visible while iconified; block instead of spinning.
            glfwWaitEvents();
        } else {
            glfwPollEvents();
        }
    }

    @Override
    public boolean isCloseRequested() {
        return glfwWindowShouldClose(window);
    }

    @Override
    public void requestClose() {
        if (window != NULL) {
            // The same flag a window close action sets, so the run loop ends through its normal path.
            glfwSetWindowShouldClose(window, true);
        }
    }

    @Override
    public WindowState windowState() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer width = stack.mallocInt(1);
            IntBuffer height = stack.mallocInt(1);
            glfwGetWindowSize(window, width, height);
            return new WindowState(new Resolution(width.get(0), height.get(0)),
                    glfwGetWindowMonitor(window) != NULL);
        }
    }

    @Override
    public void destroyWindow() {
        if (window == NULL) {
            return;
        }
        long handle = window;
        window = NULL;
        pointerListener = PointerListener.NONE;
        pointerCallbacksInstalled = false;
        glfwMakeContextCurrent(NULL);
        GL.setCapabilities(null);
        Callbacks.glfwFreeCallbacks(handle);
        glfwDestroyWindow(handle);
    }

    @Override
    public void terminate() {
        try {
            if (initialized) {
                initialized = false;
                glfwTerminate();
            }
        } finally {
            if (errorCallback != null) {
                glfwSetErrorCallback(null);
                errorCallback.free();
                errorCallback = null;
            }
        }
    }
}
