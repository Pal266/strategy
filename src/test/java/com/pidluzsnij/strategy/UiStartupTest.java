package com.pidluzsnij.strategy;

import com.pidluzsnij.strategy.logging.LoggingMode;
import com.pidluzsnij.strategy.testsupport.FakeWindowSystem;
import com.pidluzsnij.strategy.testsupport.LogHarness;
import com.pidluzsnij.strategy.testsupport.UiFixtures;
import com.pidluzsnij.strategy.ui.UiStartupConfiguration;
import com.pidluzsnij.strategy.ui.resource.UiResources;
import com.pidluzsnij.strategy.window.Resolution;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.pidluzsnij.strategy.testsupport.LogHarness.count;
import static com.pidluzsnij.strategy.testsupport.UiFixtures.button;
import static com.pidluzsnij.strategy.testsupport.UiFixtures.definition;
import static com.pidluzsnij.strategy.testsupport.UiFixtures.image;
import static com.pidluzsnij.strategy.testsupport.UiFixtures.style;
import static com.pidluzsnij.strategy.testsupport.UiFixtures.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The UI foundation within application startup: ordering, fatal failures, cleanup, diagnostics and the
 * unchanged production black screen, with isolated logging, configuration, localization and UI resources.
 */
class UiStartupTest {

    private static final String UI_LOGGER = "] com.pidluzsnij.strategy.ui.";
    private static final String TITLE = "Тестовий заголовок ěščř őű";
    private static final String DEFINITION = definition(1600, 900,
            image("background", 0, 0, 1600, 900, "images/bg.png"),
            text("title", 100, 100, 1400, 100, style("test.ui.title", "fonts/a.ttf", 40, "#FFFFFF", "center")),
            button("start", 600, 400, 400, 100, "test.start", "images/n.png", "images/h.png", "images/p.png", null));

    @TempDir
    Path temp;

    private final List<String> events = Collections.synchronizedList(new ArrayList<>());
    private final List<URLClassLoader> loaders = new ArrayList<>();
    private LogHarness harness;
    private FakeWindowSystem windowSystem;

    @AfterEach
    void closeLoaders() throws IOException {
        for (URLClassLoader loader : loaders) {
            loader.close();
        }
    }

    private static UiFixtures.ResourceSet resources() {
        return UiFixtures.resources()
                .put("screens/test.json", DEFINITION)
                .put("images/bg.png", UiFixtures.png(8, 8, 0x102030FF))
                .put("images/n.png", UiFixtures.png(2, 2, 0x00FF00FF))
                .put("images/h.png", UiFixtures.png(2, 2, 0x0000FFFF))
                .put("images/p.png", UiFixtures.png(2, 2, 0xFFFFFFFF))
                .put("fonts/a.ttf", UiFixtures.font(UiFixtures.FONT_A));
    }

    /** Prepares an isolated launch requiring the test definition. */
    private void prepare(UiFixtures.ResourceSet bundled) {
        harness = new LogHarness(temp);
        harness.localizationResources = new com.pidluzsnij.strategy.testsupport.FixtureResources()
                .put("english.properties", "application.window.title=Test window\ntest.ui.title=" + TITLE + "\n");
        URLClassLoader loader = UiFixtures.classLoader(bundled.writeBundledDirectory(temp.resolve("bundled")));
        loaders.add(loader);
        harness.uiConfiguration = UiFixtures.configuration(loader, harness.configDirectory,
                List.of("screens/test.json"), Set.of("test.start"));
        windowSystem = new FakeWindowSystem(events);
        windowSystem.onEachFrame = () -> events.add("frame");
    }

    private int launch(LoggingMode mode) {
        return harness.launch(mode, windowSystem);
    }

    private List<String> withLevel(String level) {
        return harness.records().stream().filter(r -> r.contains(" " + level + " [")).toList();
    }

    private int indexOfRecord(String text) {
        List<String> records = harness.records();
        for (int i = 0; i < records.size(); i++) {
            if (records.get(i).contains(text)) {
                return i;
            }
        }
        return -1;
    }

    // --- Startup order ----------------------------------------------------------------------

    @Test
    void uiInitializesAfterOpenGlAndBeforeTheEventLoop() {
        prepare(resources());

        assertEquals(0, launch(LoggingMode.DEVELOPMENT), harness.stderr());

        int graphics = events.indexOf("initializeGraphics");
        int uiGraphics = events.indexOf("createUiGraphics");
        int uiInitialize = events.indexOf("ui-initialize");
        // A 1920×1080 framebuffer shows the 1600×900 layout at scale 1.2: 40 logical units are 48 pixels.
        int lastUiResource = events.lastIndexOf("ui-createTextFace bundled fonts/a.ttf 48");
        int pointer = events.indexOf("setPointerListener");
        int firstFrame = events.indexOf("frame");
        assertTrue(events.indexOf("createWindow") < graphics, events.toString());
        assertTrue(graphics < uiGraphics && uiGraphics < uiInitialize, events.toString());
        assertTrue(uiInitialize < lastUiResource && lastUiResource < pointer && pointer < firstFrame, events.toString());

        int logging = indexOfRecord("Application startup begins");
        int configuration = indexOfRecord("Configuration initialized");
        int localization = indexOfRecord("Localization initialized");
        int openGl = indexOfRecord("OpenGL context initialized");
        int ui = indexOfRecord("UI foundation initialized");
        assertTrue(logging < configuration && configuration < localization && localization < openGl && openGl < ui,
                harness.records().toString());
        assertTrue(ui < indexOfRecord("Window close request received"));
    }

    // --- Fatal initialization failures ------------------------------------------------------

    enum Failure { DEFINITION, IMAGE, FONT, RENDERING }

    @ParameterizedTest
    @EnumSource(Failure.class)
    void fatalUiFailureEndsStartupWithCleanupAndNoEventLoop(Failure failure) {
        UiFixtures.ResourceSet set = resources();
        String context = switch (failure) {
            case DEFINITION -> {
                set.put("screens/test.json", "{\"layout\": {\"width\": 1, \"height\": 1}, \"components\": [");
                yield "could not load UI definition (resource: bundled screens/test.json)";
            }
            case IMAGE -> {
                set.put("images/h.png", UiFixtures.malformedPng());
                yield "could not decode UI image (resource: bundled images/h.png)";
            }
            case FONT -> {
                set.put("fonts/a.ttf", UiFixtures.truncatedFont());
                yield "could not load UI font (resource: bundled fonts/a.ttf)";
            }
            case RENDERING -> "could not initialize UI rendering";
        };
        prepare(set);
        if (failure == Failure.RENDERING) {
            windowSystem.uiGraphics.initializeFailure = new IllegalStateException("ui-rendering-probe");
        }

        int exit = launch(LoggingMode.DEFAULT);

        assertEquals(1, exit);
        List<String> errors = withLevel("ERROR");
        assertEquals(1, errors.size(), harness.records().toString());
        assertTrue(errors.get(0).contains("UI foundation initialization failed: " + context), errors.get(0));
        assertTrue(errors.get(0).contains("\tat "), "exception details are recorded");
        if (failure == Failure.RENDERING) {
            assertEquals(1, count(harness.log(), "ui-rendering-probe"));
        }
        assertFalse(harness.log().contains("Normal shutdown completed"));
        assertFalse(harness.log().contains("UI foundation initialized"));
        assertEquals(0, windowSystem.eventIterations, "the event loop is never entered");
        assertFalse(events.contains("frame"));
        assertFalse(events.contains("setPointerListener"));
        assertTrue(events.contains("ui-close"), "UI rendering resources are released");
        assertTrue(windowSystem.uiGraphics.everythingReleasedOnce());
        assertTrue(events.indexOf("ui-close") < events.indexOf("destroyWindow"), events.toString());
        assertTrue(events.indexOf("destroyWindow") < events.indexOf("terminate"), "existing cleanup is attempted");
        assertFalse(harness.log().contains(TITLE), "no translated text is logged");
    }

    @Test
    void missingRequiredResourceIsFatalWithSafeContext() {
        UiFixtures.ResourceSet set = resources();
        set.put("screens/test.json", definition(10, 10, image("x", 0, 0, 1, 1, "images/absent.png")));
        prepare(set);

        assertEquals(1, launch(LoggingMode.DEFAULT));

        List<String> errors = withLevel("ERROR");
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("could not resolve UI resource (resource: images/absent.png)"), errors.get(0));
        assertTrue(events.contains("terminate"));
    }

    @Test
    void uiGraphicsCreationFailureIsFatal() {
        prepare(resources());
        windowSystem.createUiGraphicsFailure = new IllegalStateException("ui-graphics-probe");

        assertEquals(1, launch(LoggingMode.DEFAULT));

        List<String> errors = withLevel("ERROR");
        assertEquals(1, errors.size(), harness.records().toString());
        assertTrue(errors.get(0).contains("UI initialization failed"));
        assertTrue(errors.get(0).contains("ui-graphics-probe"));
        assertEquals(0, windowSystem.eventIterations);
        assertTrue(events.contains("destroyWindow") && events.contains("terminate"));
    }

    // --- Cleanup ----------------------------------------------------------------------------

    @Test
    void normalShutdownReleasesUiBeforeTheWindow() {
        prepare(resources());

        assertEquals(0, launch(LoggingMode.DEFAULT));

        assertTrue(windowSystem.uiGraphics.everythingReleasedOnce());
        assertEquals(1, windowSystem.uiGraphics.closes);
        assertTrue(events.indexOf("ui-close") < events.indexOf("destroyWindow"));
        assertTrue(harness.records().get(harness.records().size() - 1).contains("Normal shutdown completed"));
    }

    @Test
    void uiCleanupFailureDoesNotSuppressWindowCleanup() {
        prepare(resources());
        windowSystem.uiGraphics.closeFailure = new IllegalStateException("ui-close-probe");
        windowSystem.uiGraphics.failTextureReleaseFor = "bundled images/bg.png";

        assertEquals(1, launch(LoggingMode.DEFAULT));

        List<String> errors = withLevel("ERROR");
        assertEquals(1, errors.size(), harness.records().toString());
        assertTrue(errors.get(0).contains("Application cleanup failed: release UI resources"));
        assertTrue(errors.get(0).contains("ui-close-probe"));
        assertTrue(windowSystem.uiGraphics.everythingReleasedOnce(), "later UI releases still happen");
        assertTrue(events.contains("destroyWindow") && events.contains("terminate"));
        assertFalse(harness.log().contains("Normal shutdown completed"));
    }

    @Test
    void windowCleanupFailureStillReleasesUi() {
        prepare(resources());
        windowSystem.destroyFailure = new IllegalStateException("destroy-probe");

        assertEquals(1, launch(LoggingMode.DEFAULT));

        assertTrue(windowSystem.uiGraphics.everythingReleasedOnce());
        assertTrue(events.contains("terminate"));
    }

    // --- Diagnostics ------------------------------------------------------------------------

    @Test
    void defaultModeReportsReadinessWithoutDebugRecords() {
        prepare(resources());

        assertEquals(0, launch(LoggingMode.DEFAULT));

        List<String> info = withLevel("INFO ").stream().filter(r -> r.contains(UI_LOGGER)).toList();
        assertEquals(1, info.size(), harness.records().toString());
        assertTrue(info.get(0).contains("UI foundation initialized: rendering ready, 1 UI definition(s) loaded"));
        assertTrue(withLevel("DEBUG").isEmpty());
    }

    @Test
    void developmentModeDistinguishesBundledAndExternalResolution() {
        UiFixtures.ResourceSet set = resources();
        prepare(set);
        UiFixtures.resources().put("images/h.png", UiFixtures.png(2, 2, 0xFF00FFFF)).writeExternal(harness.configDirectory);

        assertEquals(0, launch(LoggingMode.DEVELOPMENT));

        List<String> debug = withLevel("DEBUG").stream().filter(r -> r.contains(UI_LOGGER)).toList();
        Path override = UiResources.externalRoot(harness.configDirectory).resolve("images").resolve("h.png");
        assertEquals(1, debug.stream().filter(r -> r.contains("UI resource 'images/h.png' resolved from external override "
                + override)).count(), debug.toString());
        for (String bundled : List.of("screens/test.json", "images/bg.png", "images/n.png", "images/p.png", "fonts/a.ttf")) {
            assertEquals(1, debug.stream().filter(r -> r.contains("UI resource '" + bundled
                    + "' resolved from bundled resources")).count(), bundled + " " + debug);
        }
        assertTrue(harness.log().contains("external override root " + UiResources.externalRoot(harness.configDirectory)
                + " (present)"));
    }

    @Test
    void failuresInDevelopmentModeIdentifyTheExternalResource() {
        prepare(resources());
        UiFixtures.resources().put("images/h.png", UiFixtures.gif()).writeExternal(harness.configDirectory);

        assertEquals(1, launch(LoggingMode.DEVELOPMENT));

        List<String> errors = withLevel("ERROR");
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("could not decode UI image (resource: external images/h.png): "
                + "the resource is not a PNG image"), errors.get(0));
        assertTrue(harness.log().contains("UI resource 'images/h.png' resolved from external override"));
    }

    @Test
    void fontWithoutGlyphsForTheLocalizedTextIsReportedOnceWithoutTheText() {
        prepare(resources());
        // The euro sign and the CJK character are not in the test font; the other characters are.
        String uncovered = TITLE + " € 字 €";
        harness.localizationResources = new com.pidluzsnij.strategy.testsupport.FixtureResources()
                .put("english.properties", "application.window.title=Test window\ntest.ui.title=" + uncovered + "\n");

        assertEquals(0, launch(LoggingMode.DEFAULT), "missing glyphs are not fatal");

        List<String> warnings = withLevel("WARN ");
        assertEquals(1, warnings.size(), harness.records().toString());
        long distinct = uncovered.codePoints().distinct().count();
        assertTrue(warnings.get(0).contains("UI font bundled fonts/a.ttf has no glyph for 2 of the " + distinct
                + " distinct character(s) of its localized text (language 'en')"), warnings.get(0));
        assertFalse(harness.log().contains("€"), "the characters are not logged");
        assertFalse(harness.log().contains(TITLE), "the text is not logged");
    }

    @Test
    void fontCoveringTheLocalizedTextProducesNoWarning() {
        prepare(resources());
        assertEquals(0, launch(LoggingMode.DEFAULT));
        assertTrue(withLevel("WARN ").isEmpty(), harness.records().toString());
    }

    @ParameterizedTest
    @ValueSource(ints = {3, 300})
    void framesAndPointerInputProduceNoUiRecords(int frames) {
        prepare(resources());
        windowSystem.iterationsBeforeClose = frames;
        windowSystem.onEachFrame = () -> {
            windowSystem.pointerListener.pointerMoved(800, 450, 1920, 1080);
            windowSystem.pointerListener.primaryButton(true, 800, 450, 1920, 1080);
            windowSystem.pointerListener.pointerMoved(10, 10, 1920, 1080);
            windowSystem.pointerListener.primaryButton(false, 10, 10, 1920, 1080);
            windowSystem.pointerListener.pointerLeft();
        };

        assertEquals(0, launch(LoggingMode.DEVELOPMENT));

        assertTrue(windowSystem.framesRendered > frames);
        List<String> ui = harness.records().stream().filter(r -> r.contains(UI_LOGGER)).toList();
        // Six resolutions, readiness, release: independent of frame and pointer-event counts.
        assertEquals(8, ui.size(), ui.toString());
        assertTrue(withLevel("TRACE").isEmpty());
        String log = harness.log();
        assertFalse(log.contains(TITLE), "translated text is never logged");
        assertFalse(log.contains("\"layout\""), "definition content is never logged");
        assertFalse(log.contains("IHDR"), "image bytes are never logged");
    }

    // --- Production behavior ----------------------------------------------------------------

    @Test
    void productionConfigurationActivatesNoUiAndSubmitsNothing() throws URISyntaxException {
        harness = new LogHarness(temp);
        windowSystem = new FakeWindowSystem(events);
        windowSystem.iterationsBeforeClose = 50;
        windowSystem.onEachFrame = () -> windowSystem.pointerListener.primaryButton(true, 5, 5, 1920, 1080);

        assertEquals(0, launch(LoggingMode.DEVELOPMENT), harness.stderr());

        assertTrue(windowSystem.framesRendered > 50);
        assertEquals(0, windowSystem.uiGraphics.frames, "no UI frame is drawn");
        assertEquals(List.of(), windowSystem.uiGraphics.draws, "no UI element is submitted");
        assertTrue(windowSystem.uiGraphics.textures.isEmpty());
        assertTrue(windowSystem.uiGraphics.faces.isEmpty());
        assertTrue(harness.log().contains("UI foundation initialized: rendering deferred until a UI definition is "
                + "loaded, 0 UI definition(s) loaded"));
        assertFalse(events.contains("ui-initialize"), "no UI shaders or buffers are created, so no OpenGL 2.0 is needed");
        assertFalse(harness.log().contains("UI resource '"), "no UI resource is resolved");
        assertFalse(Files.exists(UiResources.externalRoot(harness.configDirectory)), "no external UI directory is created");
    }

    @Test
    void normalStartupUsesTheProductionUiConfiguration() throws Exception {
        UiStartupConfiguration production = ApplicationLauncher.forNormalStartup().uiConfiguration();

        assertEquals(List.of(), production.requiredDefinitions(), "no production UI definition is activated");
        assertEquals(Set.of(), production.supportedBehaviors(), "no production behavior is defined");
        assertSame(ApplicationLauncher.class.getClassLoader(), production.bundledResources());
        assertTrue(production.location() instanceof com.pidluzsnij.strategy.config.persistence.DirectoriesConfigurationLocation);

        Path classes = Path.of(ApplicationLauncher.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        assertFalse(Files.exists(classes.resolve("ui")), "no production UI resources are bundled");
        assertNull(ApplicationLauncher.class.getClassLoader().getResource("ui/screens/test.json"));
    }

    @Test
    void windowBehaviorIsUnchangedBySuccessfulUiInitialization() {
        prepare(resources());
        windowSystem.framebuffer = new Resolution(1920, 1080);

        assertEquals(0, launch(LoggingMode.DEFAULT));

        assertEquals("Test window", windowSystem.createdSettings.title());
        assertTrue(windowSystem.createdSettings.fullscreen());
        assertEquals(new Resolution(1920, 1080), windowSystem.createdResolution);
        assertTrue(harness.log().contains("Window opened displaying black: title 'Test window', resolution 1920×1080, "
                + "fullscreen true"));
        assertEquals(0, windowSystem.uiGraphics.frames, "the required test definition is loaded but not shown");
    }
}
