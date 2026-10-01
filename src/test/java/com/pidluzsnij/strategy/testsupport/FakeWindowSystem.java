package com.pidluzsnij.strategy.testsupport;

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
    }

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
    public void renderBlackFrame() {
        framesRendered++;
    }

    @Override
    public void processEvents() {
        eventIterations++;
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
