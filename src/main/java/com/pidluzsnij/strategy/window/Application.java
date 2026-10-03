package com.pidluzsnij.strategy.window;

import com.pidluzsnij.strategy.config.ApplicationSettings;
import com.pidluzsnij.strategy.config.VideoResolution;
import com.pidluzsnij.strategy.config.VideoSettings;
import com.pidluzsnij.strategy.localization.Localization;
import com.pidluzsnij.strategy.ui.UiException;
import com.pidluzsnij.strategy.ui.UiFoundation;
import com.pidluzsnij.strategy.ui.UiStartup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Runs the window lifecycle: initialization (GLFW, window, OpenGL, then the UI foundation), presentation
 * until a close request, and cleanup. Frames are cleared to black and the UI foundation draws the screen it
 * shows over them: the {@linkplain UiStartup#firstScreen() first screen} of the supplied UI startup, which may
 * request the same normal shutdown as a window close action. Each failure is logged once: window failures here, UI
 * foundation initialization failures by the UI foundation.
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
    private final VideoResolution configuredResolution;
    private final ApplicationSettings applicationSettings;
    private final Localization localization;
    private final UiStartup uiStartup;
    private UiFoundation ui;

    public Application(WindowSystem windowSystem, RuntimeEnvironment runtime,
                       ApplicationSettings applicationSettings, Localization localization,
                       UiStartup uiStartup) {
        this.windowSystem = windowSystem;
        this.uiStartup = Objects.requireNonNull(uiStartup, "uiStartup");
        this.runtime = runtime;
        this.localization = Objects.requireNonNull(localization, "localization");
        // The title is resolved once, after localization succeeded and before the window is created.
        this.settings = WindowSettings.from(applicationSettings, this.localization);
        this.configuredResolution = applicationSettings.get(VideoSettings.RESOLUTION);
        this.applicationSettings = applicationSettings;
    }

    /** @return the localization initialized for this application run */
    public Localization localization() {
        return localization;
    }

    /** @return the complete effective application settings this application was started with */
    public ApplicationSettings applicationSettings() {
        return applicationSettings;
    }

    /** @return {@link #EXIT_SUCCESS} after a normal shutdown, otherwise {@link #EXIT_FAILURE} */
    public int run() {
        log.debug("Runtime environment: Java {}, OS {} {}, architecture {}, LWJGL {}",
                orUnavailable(runtime.javaVersion()), orUnavailable(runtime.osName()),
                orUnavailable(runtime.osVersion()), orUnavailable(runtime.architecture()),
                orUnavailable(runtime.lwjglVersion()));
        log.debug("Effective video settings selected for startup: fullscreen {}, resolution selection {}",
                settings.fullscreen(), describeSelection(configuredResolution));

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
            Resolution framebuffer = windowSystem.framebufferSize();
            ui = UiFoundation.initialize(uiStartup.configuration(), windowSystem.createUiGraphics(), localization,
                    framebuffer.width(), framebuffer.height());
            windowSystem.setPointerListener(ui.pointerListener());
            uiStartup.firstScreen().show(ui, windowSystem::requestClose);
        } catch (UiException e) {
            // Already logged by the UI foundation, which also released what it had allocated.
            cleanUp();
            return EXIT_FAILURE;
        } catch (RuntimeException | LinkageError e) {
            return failStartup("UI initialization", e);
        }

        UiFoundation frameUi = ui;
        FrameOverlay overlay = frameUi::render;
        try {
            while (true) {
                windowSystem.processEvents();
                if (windowSystem.isCloseRequested()) {
                    break;
                }
                windowSystem.renderFrame(overlay);
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
        ResolvedResolution resolved = ResolutionResolver.resolve(configuredResolution, mode);
        if (resolved.source() == ResolutionSource.AUTOMATIC_FALLBACK) {
            String reason = monitor.unavailableReason();
            log.warn("Starting monitor resolution could not be determined (reason: {}); using fallback resolution {}",
                    reason == null ? UNAVAILABLE : reason, Resolution.FALLBACK);
        }
        log.debug("Startup resolution resolved: {} (source: {})", resolved.resolution(), resolved.source());
        return resolved.resolution();
    }

    private static String describeSelection(VideoResolution resolution) {
        return resolution instanceof VideoResolution.Explicit explicit
                ? "explicit " + explicit
                : "automatic";
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
        boolean uiReleased = true;
        if (ui != null) {
            UiFoundation released = ui;
            ui = null;
            uiReleased = cleanUpStep("release UI resources", released::close);
        }
        boolean destroyed = cleanUpStep("destroy window", windowSystem::destroyWindow);
        boolean terminated = cleanUpStep("terminate GLFW", windowSystem::terminate);
        if (uiReleased && destroyed && terminated) {
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
