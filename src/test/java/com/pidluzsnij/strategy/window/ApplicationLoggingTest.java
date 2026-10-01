package com.pidluzsnij.strategy.window;

import com.pidluzsnij.strategy.logging.FileOperations;
import com.pidluzsnij.strategy.logging.LoggingMode;
import com.pidluzsnij.strategy.testsupport.FakeWindowSystem;
import com.pidluzsnij.strategy.testsupport.LogHarness;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import static com.pidluzsnij.strategy.testsupport.LogHarness.count;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Window lifecycle events in each logging mode. */
class ApplicationLoggingTest {

    @TempDir
    Path temp;

    private LogHarness harness;

    private List<String> run(LoggingMode mode, FakeWindowSystem windowSystem, int expectedExit) {
        harness = new LogHarness(temp);
        int exit = harness.launch(mode, windowSystem);
        assertEquals(expectedExit, exit, harness.stderr());
        return harness.records();
    }

    private static List<String> withLevel(List<String> records, String level) {
        return records.stream().filter(r -> r.contains(" " + level + " [")).toList();
    }

    /** Records other than the configuration events, which have their own tests. */
    private static List<String> withoutConfiguration(List<String> records) {
        return records.stream().filter(r -> !r.contains("] com.pidluzsnij.strategy.ConfigurationStartup - ")).toList();
    }

    private static boolean has(List<String> records, String level, String text) {
        return records.stream().anyMatch(r -> r.contains(" " + level) && r.contains(text));
    }

    // --- Default-mode window events -------------------------------------------------------

    @ParameterizedTest
    @EnumSource(LoggingMode.class)
    void successfulStartupAndShutdown(LoggingMode mode) {
        List<String> records = run(mode, new FakeWindowSystem(), 0);

        assertTrue(has(records, "INFO ", "Application startup begins (logging mode: " + mode.id() + ")"));
        assertTrue(has(records, "INFO ",
                "Window opened displaying black: title 'My strategy', resolution 1920×1080, fullscreen true"));
        assertTrue(has(records, "INFO ", "Normal shutdown completed"));
        assertTrue(records.get(records.size() - 1).contains("Normal shutdown completed"),
                "normal termination is the final record");
        assertTrue(withLevel(records, "WARN ").isEmpty());
        assertTrue(withLevel(records, "ERROR").isEmpty());
        assertTrue(records.stream().allMatch(r -> r.contains("[" + Thread.currentThread().getName() + "]")));
    }

    @ParameterizedTest
    @EnumSource(LoggingMode.class)
    void unavailableMonitorResolutionWarnsAndFallsBack(LoggingMode mode) {
        FakeWindowSystem windowSystem = new FakeWindowSystem();
        windowSystem.monitor = MonitorInfo.unavailable("simulated no video mode");

        List<String> records = run(mode, windowSystem, 0);

        assertEquals(new Resolution(1280, 720), windowSystem.createdResolution);
        List<String> warnings = withLevel(records, "WARN ");
        assertEquals(1, warnings.size(), records.toString());
        assertTrue(warnings.get(0).contains("simulated no video mode"));
        assertTrue(warnings.get(0).contains("1280×720"));
        assertTrue(has(records, "INFO ", "resolution 1280×720"));
    }

    @ParameterizedTest
    @EnumSource(LoggingMode.class)
    void glfwInitializationFailure(LoggingMode mode) {
        FakeWindowSystem windowSystem = new FakeWindowSystem();
        windowSystem.initializeFailure = new IllegalStateException("glfw-init-probe");
        assertStageFailure(mode, windowSystem, "GLFW initialization failed", "glfw-init-probe");
    }

    @ParameterizedTest
    @EnumSource(LoggingMode.class)
    void windowCreationFailure(LoggingMode mode) {
        FakeWindowSystem windowSystem = new FakeWindowSystem();
        windowSystem.createWindowFailure = new IllegalStateException("window-creation-probe");
        assertStageFailure(mode, windowSystem, "Window creation failed", "window-creation-probe");
    }

    @ParameterizedTest
    @EnumSource(LoggingMode.class)
    void openGlInitializationFailure(LoggingMode mode) {
        FakeWindowSystem windowSystem = new FakeWindowSystem();
        windowSystem.graphicsFailure = new IllegalStateException("opengl-init-probe");
        assertStageFailure(mode, windowSystem, "OpenGL initialization failed", "opengl-init-probe");
    }

    private void assertStageFailure(LoggingMode mode, FakeWindowSystem windowSystem, String message, String probe) {
        List<String> records = run(mode, windowSystem, 1);
        String log = harness.log();

        List<String> errors = withLevel(records, "ERROR");
        assertEquals(1, errors.size(), records.toString());
        assertTrue(errors.get(0).contains(message));
        assertTrue(errors.get(0).contains("java.lang.IllegalStateException: " + probe));
        assertTrue(errors.get(0).contains("\tat "), "stack trace");
        assertEquals(1, count(log, probe), "the failure's exception is recorded once");
        assertFalse(log.contains("Normal shutdown completed"));
        assertTrue(windowSystem.events.contains("terminate"), "cleanup is attempted");
    }

    @ParameterizedTest
    @EnumSource(LoggingMode.class)
    void glfwErrorCallbackUsesTheLogger(LoggingMode mode) {
        FakeWindowSystem windowSystem = new FakeWindowSystem();
        windowSystem.glfwErrorCode = 0x00010008;
        windowSystem.glfwErrorDescription = "simulated platform error";
        windowSystem.initializeFailure = new IllegalStateException("glfwInit returned false");

        List<String> records = run(mode, windowSystem, 1);

        List<String> errors = withLevel(records, "ERROR");
        assertEquals(2, errors.size(), records.toString());
        assertTrue(errors.get(0).contains("] " + Application.GLFW_LOGGER_NAME + " - GLFW error 0x00010008: "
                + "simulated platform error"), errors.get(0));
        assertTrue(errors.get(1).contains("GLFW initialization failed"));
        assertEquals(1, count(harness.log(), "glfwInit returned false"));
        assertTrue(harness.stderr().isEmpty(), "GLFW errors go to the log, not stderr");
    }

    @ParameterizedTest
    @EnumSource(LoggingMode.class)
    void cleanupFailureSuppressesNormalTermination(LoggingMode mode) {
        FakeWindowSystem windowSystem = new FakeWindowSystem();
        windowSystem.destroyFailure = new IllegalStateException("destroy-probe");

        List<String> records = run(mode, windowSystem, 1);
        String log = harness.log();

        List<String> errors = withLevel(records, "ERROR");
        assertEquals(1, errors.size(), records.toString());
        assertTrue(errors.get(0).contains("Application cleanup failed: destroy window"));
        assertTrue(errors.get(0).contains("java.lang.IllegalStateException: destroy-probe"));
        assertTrue(errors.get(0).contains("\tat "));
        assertEquals(1, count(log, "destroy-probe"));
        assertFalse(log.contains("Normal shutdown completed"));
        assertFalse(log.contains("resources released"));
        assertTrue(windowSystem.events.contains("terminate"), "remaining cleanup is still attempted");
    }

    // --- Development-mode diagnostics -----------------------------------------------------

    @ParameterizedTest
    @EnumSource(LoggingMode.class)
    void developmentDiagnosticsWithKnownValues(LoggingMode mode) {
        FakeWindowSystem windowSystem = new FakeWindowSystem();
        windowSystem.monitor = new MonitorInfo("Probe Monitor 27", new VideoMode(2560, 1440, 144), null);
        windowSystem.graphics = new GraphicsInfo("4.6.0 Probe", "Probe Vendor", "Probe Renderer");

        List<String> records = run(mode, windowSystem, 0);
        List<String> debug = withLevel(withoutConfiguration(records), "DEBUG");

        if (mode == LoggingMode.DEFAULT) {
            assertTrue(withLevel(records, "DEBUG").isEmpty(), records.toString());
            return;
        }
        assertEquals(5, debug.size(), debug.toString());
        assertTrue(has(debug, "DEBUG", "Java 21.0.42-test, OS TestOS 9.8.7, architecture test-arch, LWJGL 3.4.3-test"));
        assertTrue(has(debug, "DEBUG", "Starting monitor: name Probe Monitor 27, resolution 2560×1440, "
                + "refresh rate 144 Hz"));
        assertTrue(has(debug, "DEBUG", "OpenGL context initialized: version 4.6.0 Probe, vendor Probe Vendor, "
                + "renderer Probe Renderer"));
        assertTrue(has(debug, "DEBUG", "Window close request received"));
        assertTrue(has(debug, "DEBUG", "Window and graphics resources released"));
        assertFalse(harness.log().contains("Alt"), "close source must not be attributed to Alt+F4");
    }

    @ParameterizedTest
    @EnumSource(LoggingMode.class)
    void developmentDiagnosticsIdentifyUnavailableValues(LoggingMode mode) {
        FakeWindowSystem windowSystem = new FakeWindowSystem();
        windowSystem.monitor = MonitorInfo.unavailable("no monitor");
        windowSystem.graphics = new GraphicsInfo(null, null, null);

        List<String> records = run(mode, windowSystem, 0);
        List<String> debug = withLevel(records, "DEBUG");

        if (mode == LoggingMode.DEFAULT) {
            assertTrue(debug.isEmpty(), debug.toString());
            return;
        }
        assertTrue(has(debug, "DEBUG",
                "Starting monitor: name unavailable, resolution unavailable, refresh rate unavailable"));
        assertTrue(has(debug, "DEBUG",
                "OpenGL context initialized: version unavailable, vendor unavailable, renderer unavailable"));
    }

    // --- No event-loop log spam -----------------------------------------------------------

    @Test
    void repeatedFramesAndEventIterationsProduceNoRecords() {
        FakeWindowSystem windowSystem = new FakeWindowSystem();
        windowSystem.iterationsBeforeClose = 500;

        List<String> records = run(LoggingMode.DEVELOPMENT, windowSystem, 0);

        assertTrue(windowSystem.framesRendered > 500);
        assertTrue(withLevel(records, "TRACE").isEmpty());
        // Startup, runtime, monitor, OpenGL, window opened, close request, release, normal shutdown.
        assertEquals(8, withoutConfiguration(records).size(), records.toString());
    }

    // --- Shutdown ordering and resource release -------------------------------------------

    @Test
    void cleanupPrecedesTerminationRecordWhichPrecedesOwnershipRelease() {
        List<String> events = Collections.synchronizedList(new ArrayList<>());
        Consumer<String> event = events::add;
        FileOperations recording = new FileOperations() {
            @Override
            public void write(FileChannel channel, ByteBuffer data) throws IOException {
                String text = StandardCharsets.UTF_8.decode(data.duplicate()).toString();
                FileOperations.super.write(channel, data);
                event.accept("write:" + text.strip());
            }

            @Override
            public void release(FileLock lock) throws IOException {
                event.accept("release");
                FileOperations.super.release(lock);
            }

            @Override
            public void close(FileChannel channel) throws IOException {
                event.accept("close");
                FileOperations.super.close(channel);
            }
        };
        FakeWindowSystem windowSystem = new FakeWindowSystem(events);
        harness = new LogHarness(temp);

        int exit = harness.launch(LoggingMode.DEVELOPMENT, recording, harness.location(), windowSystem);

        assertEquals(0, exit);
        int destroy = events.indexOf("destroyWindow");
        int terminate = events.indexOf("terminate");
        int released = indexOfWrite(events, "Window and graphics resources released");
        int normal = indexOfWrite(events, "Normal shutdown completed");
        int release = events.indexOf("release");
        int close = events.indexOf("close");
        assertTrue(destroy >= 0 && destroy < terminate, events.toString());
        assertTrue(terminate < released && released < normal, events.toString());
        assertTrue(normal < release && release < close, events.toString());
        assertEquals(close, events.size() - 1, "nothing is written after logging closes");
    }

    private static int indexOfWrite(List<String> events, String text) {
        for (int i = 0; i < events.size(); i++) {
            if (events.get(i).startsWith("write:") && events.get(i).contains(text)) {
                return i;
            }
        }
        return -1;
    }
}
