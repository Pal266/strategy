package com.pidluzsnij.strategy.testsupport;

import com.pidluzsnij.strategy.ui.input.PointerListener;
import com.pidluzsnij.strategy.ui.render.UiGraphics;
import com.pidluzsnij.strategy.window.FrameOverlay;
import com.pidluzsnij.strategy.window.GlfwErrorListener;
import com.pidluzsnij.strategy.window.GraphicsInfo;
import com.pidluzsnij.strategy.window.MonitorInfo;
import com.pidluzsnij.strategy.window.Resolution;
import com.pidluzsnij.strategy.window.VideoMode;
import com.pidluzsnij.strategy.window.WindowSettings;
import com.pidluzsnij.strategy.window.WindowState;
import com.pidluzsnij.strategy.window.WindowSystem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Controllable {@link WindowSystem} that records calls and can fail at any stage. */
public final class FakeWindowSystem implements WindowSystem {

    public final List<String> events;

    public MonitorInfo monitor = new MonitorInfo("Fake Monitor", new VideoMode(1920, 1080, 60), null);
    public GraphicsInfo graphics = new GraphicsInfo("4.6.0 Fake", "Fake Vendor", "Fake Renderer");
    public RuntimeException initializeFailure;
    public RuntimeException monitorFailure;
    public RuntimeException createWindowFailure;
    public RuntimeException graphicsFailure;
    public RuntimeException destroyFailure;
    public RuntimeException terminateFailure;
    /** GLFW error {code, description} emitted through the listener during initialize. */
    public Integer glfwErrorCode;
    public String glfwErrorDescription;
    /** Number of event-processing iterations before the close request appears. */
    public int iterationsBeforeClose;
    public Runnable onInitialize = () -> { };
    public Runnable onProcessEvents = () -> { };
    public Runnable onDestroyWindow = () -> { };

    /** State reported by the platform after creation; by default the requested settings and resolution. */
    public WindowState actualState;

    /** UI drawing backend handed to the UI foundation. */
    public RecordingUiGraphics uiGraphics;
    /** Framebuffer size; by default the created window resolution. */
    public Resolution framebuffer;
    /** Pointer listener installed by the application, for injecting pointer input. */
    public PointerListener pointerListener;
    public RuntimeException createUiGraphicsFailure;
    /** Runs at the start of each event-processing iteration, before close handling. */
    public Runnable onEachFrame = () -> { };

    public WindowSettings createdSettings;
    public Resolution createdResolution;
    public int framesRendered;
    public int eventIterations;
    private boolean closeRequested;

    public FakeWindowSystem() {
        this(Collections.synchronizedList(new ArrayList<>()));
    }

    public FakeWindowSystem(List<String> events) {
        this.events = events;
        this.uiGraphics = new RecordingUiGraphics(events);
    }

    @Override
    public void requestClose() {
        closeRequested = true;
    }

    @Override
    public void initialize(GlfwErrorListener errorListener) {
        events.add("initialize");
        onInitialize.run();
        if (glfwErrorCode != null) {
            errorListener.onError(glfwErrorCode, glfwErrorDescription);
        }
        if (initializeFailure != null) {
            throw initializeFailure;
        }
    }

    @Override
    public MonitorInfo startingMonitor() {
        events.add("startingMonitor");
        if (monitorFailure != null) {
            throw monitorFailure;
        }
        return monitor;
    }

    @Override
    public void createWindow(WindowSettings settings, Resolution resolution) {
        events.add("createWindow");
        if (createWindowFailure != null) {
            throw createWindowFailure;
        }
        createdSettings = settings;
        createdResolution = resolution;
    }

    @Override
    public GraphicsInfo initializeGraphics() {
        events.add("initializeGraphics");
        if (graphicsFailure != null) {
            throw graphicsFailure;
        }
        return graphics;
    }

    @Override
    public void renderFrame(FrameOverlay overlay) {
        framesRendered++;
        Resolution size = framebufferSize();
        overlay.draw(size.width(), size.height());
    }

    @Override
    public Resolution framebufferSize() {
        return framebuffer != null ? framebuffer : createdResolution;
    }

    @Override
    public UiGraphics createUiGraphics() {
        events.add("createUiGraphics");
        if (createUiGraphicsFailure != null) {
            throw createUiGraphicsFailure;
        }
        return uiGraphics;
    }

    @Override
    public void setPointerListener(PointerListener listener) {
        events.add("setPointerListener");
        pointerListener = listener;
    }

    @Override
    public void processEvents() {
        eventIterations++;
        onEachFrame.run();
        onProcessEvents.run();
        if (eventIterations > iterationsBeforeClose) {
            closeRequested = true;
        }
    }

    @Override
    public boolean isCloseRequested() {
        return closeRequested;
    }

    @Override
    public WindowState windowState() {
        if (actualState != null) {
            return actualState;
        }
        return new WindowState(createdResolution, createdSettings.fullscreen());
    }

    @Override
    public void destroyWindow() {
        events.add("destroyWindow");
        onDestroyWindow.run();
        if (destroyFailure != null) {
            throw destroyFailure;
        }
    }

    @Override
    public void terminate() {
        events.add("terminate");
        if (terminateFailure != null) {
            throw terminateFailure;
        }
    }
}
