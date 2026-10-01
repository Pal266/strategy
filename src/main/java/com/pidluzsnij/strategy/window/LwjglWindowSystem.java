package com.pidluzsnij.strategy.window;

import org.lwjgl.glfw.Callbacks;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;
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
import static org.lwjgl.glfw.GLFW.glfwGetMonitorName;
import static org.lwjgl.glfw.GLFW.glfwGetPrimaryMonitor;
import static org.lwjgl.glfw.GLFW.glfwGetVideoMode;
import static org.lwjgl.glfw.GLFW.glfwGetWindowAttrib;
import static org.lwjgl.glfw.GLFW.glfwGetWindowMonitor;
import static org.lwjgl.glfw.GLFW.glfwGetWindowSize;
import static org.lwjgl.glfw.GLFW.glfwInit;
import static org.lwjgl.glfw.GLFW.glfwMakeContextCurrent;
import static org.lwjgl.glfw.GLFW.glfwPollEvents;
import static org.lwjgl.glfw.GLFW.glfwSetErrorCallback;
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
    public void renderBlackFrame() {
        glClearColor(0f, 0f, 0f, 1f);
        glClear(GL_COLOR_BUFFER_BIT);
        glfwSwapBuffers(window);
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
