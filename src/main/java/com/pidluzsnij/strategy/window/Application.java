package com.pidluzsnij.strategy.window;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs the window lifecycle: initialization, black-screen presentation until a close
 * request, and cleanup. Each failure is logged once, here.
 */
public final class Application {

    public static final int EXIT_SUCCESS = 0;
    public static final int EXIT_FAILURE = 1;

    static final String GLFW_LOGGER_NAME = "com.pidluzsnij.strategy.window.GLFW";
    private static final String UNAVAILABLE = "unavailable";

    private final Logger log = LoggerFactory.getLogger(Application.class);
    private final Logger glfwLog = LoggerFactory.getLogger(GLFW_LOGGER_NAME);
    private final WindowSystem windowSystem;
    private final RuntimeEnvironment runtime;
    private final WindowSettings settings;

    public Application(WindowSystem windowSystem, RuntimeEnvironment runtime) {
        this.windowSystem = windowSystem;
        this.runtime = runtime;
        this.settings = WindowSettings.initial();
    }

    /** @return {@link #EXIT_SUCCESS} after a normal shutdown, otherwise {@link #EXIT_FAILURE} */
    public int run() {
        log.debug("Runtime environment: Java {}, OS {} {}, architecture {}, LWJGL {}",
                orUnavailable(runtime.javaVersion()), orUnavailable(runtime.osName()),
                orUnavailable(runtime.osVersion()), orUnavailable(runtime.architecture()),
                orUnavailable(runtime.lwjglVersion()));

        try {
            windowSystem.initialize(this::onGlfwError);
        } catch (RuntimeException | LinkageError e) {
            return failStartup("GLFW initialization", e);
        }

        Resolution resolution = resolveResolution();

        try {
            windowSystem.createWindow(settings, resolution);
        } catch (RuntimeException | LinkageError e) {
            return failStartup("Window creation", e);
        }

        try {
            GraphicsInfo graphics = windowSystem.initializeGraphics();
            log.debug("OpenGL context initialized: version {}, vendor {}, renderer {}",
                    orUnavailable(graphics.version()), orUnavailable(graphics.vendor()),
                    orUnavailable(graphics.renderer()));
            windowSystem.renderBlackFrame();
        } catch (RuntimeException | LinkageError e) {
            return failStartup("OpenGL initialization", e);
        }

        WindowState state = windowSystem.windowState();
        log.info("Window opened displaying black: title '{}', resolution {}, fullscreen {}",
                settings.title(), state.resolution(), state.fullscreen());

        try {
            while (true) {
                windowSystem.processEvents();
                if (windowSystem.isCloseRequested()) {
                    break;
                }
                windowSystem.renderBlackFrame();
            }
        } catch (RuntimeException e) {
            log.error("Window event processing or rendering failed", e);
            cleanUp();
            return EXIT_FAILURE;
        }
        log.debug("Window close request received");

        if (!cleanUp()) {
            return EXIT_FAILURE;
        }
        log.info("Normal shutdown completed");
        return EXIT_SUCCESS;
    }

    private Resolution resolveResolution() {
        MonitorInfo monitor;
        try {
            monitor = windowSystem.startingMonitor();
        } catch (RuntimeException e) {
            monitor = MonitorInfo.unavailable("monitor query failed: " + e);
        }
        VideoMode mode = monitor.videoMode();
        if (log.isDebugEnabled()) {
            log.debug("Starting monitor: name {}, resolution {}, refresh rate {}",
                    orUnavailable(monitor.name()),
                    mode == null ? UNAVAILABLE : mode.width() + "×" + mode.height(),
                    mode == null || mode.refreshRate() <= 0 ? UNAVAILABLE : mode.refreshRate() + " Hz");
        }
        Resolution resolution = ResolutionResolver.resolve(mode);
        if (!ResolutionResolver.isDetermined(mode)) {
            String reason = monitor.unavailableReason();
            log.warn("Starting monitor resolution could not be determined (reason: {}); using fallback resolution {}",
                    reason == null ? UNAVAILABLE : reason, Resolution.FALLBACK);
        }
        return resolution;
    }

    private void onGlfwError(int code, String description) {
        glfwLog.error("GLFW error 0x{}: {}", String.format("%08X", code), description);
    }

    private int failStartup(String stage, Throwable cause) {
        log.error("{} failed", stage, cause);
        cleanUp();
        return EXIT_FAILURE;
    }

    /** @return whether every cleanup operation succeeded */
    private boolean cleanUp() {
        boolean destroyed = cleanUpStep("destroy window", windowSystem::destroyWindow);
        boolean terminated = cleanUpStep("terminate GLFW", windowSystem::terminate);
        if (destroyed && terminated) {
            log.debug("Window and graphics resources released");
            return true;
        }
        return false;
    }

    private boolean cleanUpStep(String operation, Runnable step) {
        try {
            step.run();
            return true;
        } catch (RuntimeException | LinkageError e) {
            log.error("Application cleanup failed: {}", operation, e);
            return false;
        }
    }

    private static String orUnavailable(String value) {
        return value == null || value.isBlank() ? UNAVAILABLE : value;
    }
}
