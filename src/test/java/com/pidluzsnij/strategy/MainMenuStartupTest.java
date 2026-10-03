package com.pidluzsnij.strategy;

import com.pidluzsnij.strategy.logging.LoggingMode;
import com.pidluzsnij.strategy.menu.MainMenu;
import com.pidluzsnij.strategy.testsupport.FakeWindowSystem;
import com.pidluzsnij.strategy.testsupport.LogHarness;
import com.pidluzsnij.strategy.testsupport.RecordingUiGraphics;
import com.pidluzsnij.strategy.testsupport.UiFixtures;
import com.pidluzsnij.strategy.ui.UiStartupConfiguration;
import com.pidluzsnij.strategy.ui.resource.UiResources;
import com.pidluzsnij.strategy.window.Resolution;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.pidluzsnij.strategy.testsupport.LogHarness.count;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SPEC007 within application startup and shutdown: the production configuration loads and shows the main
 * menu, Exit leaves through the normal shutdown path, and window-close shutdown is unchanged. Logging,
 * configuration and the external UI directory are isolated; the bundled production resources are used.
 */
class MainMenuStartupTest {

    /** Centers of the menu buttons in a 1920×1080 framebuffer (logical canvas at scale 1). */
    private static final double[] NEW_GAME = {960, 390};
    private static final double[] EXIT = {960, 810};
    private static final String NORMAL_SHUTDOWN = "Normal shutdown completed";

    @TempDir
    Path temp;

    private final List<String> events = Collections.synchronizedList(new ArrayList<>());
    private LogHarness harness;
    private FakeWindowSystem windowSystem;

    private void prepare() {
        harness = new LogHarness(temp);
        windowSystem = new FakeWindowSystem(events);
        windowSystem.framebuffer = new Resolution(1920, 1080);
        windowSystem.onEachFrame = () -> events.add("frame");
    }

    private int launch(LoggingMode mode) {
        return harness.launch(mode, windowSystem);
    }

    private List<String> drawnTextures() {
        return windowSystem.uiGraphics.draws.stream()
                .filter(d -> d instanceof RecordingUiGraphics.ImageDraw)
                .map(d -> ((RecordingUiGraphics.ImageDraw) d).texture()).toList();
    }

    @Test
    void normalStartupUsesTheMainMenuUiConfiguration() {
        ApplicationLauncher launcher = ApplicationLauncher.forNormalStartup();
        UiStartupConfiguration production = launcher.uiConfiguration();

        assertEquals(List.of(MainMenu.DEFINITION), production.requiredDefinitions());
        assertEquals(MainMenu.BEHAVIORS, production.supportedBehaviors());
        assertSame(ApplicationLauncher.class.getClassLoader(), production.bundledResources());
        assertTrue(production.location() instanceof com.pidluzsnij.strategy.config.persistence.DirectoriesConfigurationLocation);
        assertSame(production, launcher.uiStartup().configuration(), "one configuration, paired with its first screen");
        assertTrue(launcher.uiStartup().firstScreen() != com.pidluzsnij.strategy.ui.UiStartup.FirstScreen.NONE,
                "production shows a first screen");
    }

    @Test
    void misconfiguredStartupThatCannotShowTheMainMenuFailsStartup() {
        prepare();
        windowSystem.iterationsBeforeClose = 100;
        // The main menu is the first screen, but the configuration does not load its definition.
        harness.uiStartup = new com.pidluzsnij.strategy.ui.UiStartup(new UiStartupConfiguration(
                MainMenu.class.getClassLoader(), () -> harness.configDirectory, List.of(), MainMenu.BEHAVIORS),
                MainMenu::show);

        assertEquals(1, launch(LoggingMode.DEFAULT));

        assertEquals(0, windowSystem.eventIterations, "the event loop is never entered");
        List<String> errors = harness.records().stream().filter(r -> r.contains(" ERROR [")).toList();
        assertEquals(1, errors.size(), harness.log());
        assertTrue(errors.get(0).contains("UI initialization failed"), errors.get(0));
        assertTrue(harness.log().contains("definitions/main-menu.json' was not loaded"), harness.log());
        List<String> tail = events.subList(events.size() - 3, events.size());
        assertEquals(List.of("ui-close", "destroyWindow", "terminate"), tail, "existing failed-startup cleanup");
        assertFalse(harness.log().contains(NORMAL_SHUTDOWN));
    }

    @Test
    void successfulStartupLoadsTheMainMenuAsARequiredDefinitionAndShowsIt() {
        prepare();
        windowSystem.iterationsBeforeClose = 3;

        assertEquals(0, launch(LoggingMode.DEVELOPMENT), harness.stderr());

        String log = harness.log();
        assertTrue(log.contains("UI foundation initialized: rendering ready, 1 UI definition(s) loaded"), log);
        assertTrue(log.contains("UI resource 'definitions/main-menu.json' resolved from bundled resources"), log);
        assertTrue(events.indexOf("ui-initialize") < events.indexOf("setPointerListener"), events.toString());
        assertTrue(events.indexOf("setPointerListener") < events.indexOf("frame"), events.toString());
        // The main menu is the active screen from the first frame on.
        // Every event-loop frame draws the menu; only the initial black frame before UI startup does not.
        assertEquals(windowSystem.framesRendered - 1, windowSystem.uiGraphics.frames);
        assertTrue(windowSystem.uiGraphics.frames > 0);
        assertEquals("bundled images/main-menu/background.png", drawnTextures().get(0));
        assertTrue(log.contains(NORMAL_SHUTDOWN));
        assertFalse(Files.exists(UiResources.externalRoot(harness.configDirectory)), "no external UI directory is created");
    }

    @Test
    void exitActivationPerformsTheNormalShutdown() {
        prepare();
        windowSystem.iterationsBeforeClose = 1_000;
        // Pointer input arrives during event processing, as GLFW delivers it from glfwPollEvents.
        windowSystem.onProcessEvents = () -> {
            if (windowSystem.eventIterations == 3) {
                windowSystem.pointerListener.pointerMoved(EXIT[0], EXIT[1], 1920, 1080);
                windowSystem.pointerListener.primaryButton(true, EXIT[0], EXIT[1], 1920, 1080);
            } else if (windowSystem.eventIterations == 4) {
                windowSystem.pointerListener.primaryButton(false, EXIT[0], EXIT[1], 1920, 1080);
            }
        };

        assertEquals(0, launch(LoggingMode.DEVELOPMENT), harness.stderr());

        assertEquals(4, windowSystem.eventIterations, "the run loop ends in the iteration that activated Exit");
        assertEquals(3, windowSystem.uiGraphics.frames, "no frame is rendered after the close request");
        List<String> lifecycle = events.stream().filter(e -> !e.startsWith("ui-create") && !e.equals("frame")).toList();
        int uiClose = lifecycle.indexOf("ui-close");
        assertTrue(uiClose > 0, events.toString());
        assertEquals(List.of("ui-close", "destroyWindow", "terminate"), lifecycle.subList(uiClose, lifecycle.size()),
                "normal UI, window and GLFW cleanup");
        assertEquals(5, windowSystem.uiGraphics.textures.stream().filter(t -> t.releases == 1).count(),
                "every menu texture is released once");
        String log = harness.log();
        assertTrue(log.contains("Main menu Exit activated; requesting normal shutdown"), log);
        assertTrue(log.contains("Window close request received"), log);
        assertEquals(1, count(log, NORMAL_SHUTDOWN));
        assertTrue(harness.records().stream().anyMatch(r -> r.contains(" INFO  [") && r.contains(NORMAL_SHUTDOWN)));
        assertFalse(harness.records().stream().anyMatch(r -> r.contains(" ERROR [")), log);
    }

    @Test
    void windowCloseShutdownIsUnchangedWithTheMainMenuActive() {
        prepare();
        windowSystem.iterationsBeforeClose = 5;
        // Clicking a disabled button does not end the application.
        windowSystem.onProcessEvents = () -> {
            windowSystem.pointerListener.primaryButton(true, NEW_GAME[0], NEW_GAME[1], 1920, 1080);
            windowSystem.pointerListener.primaryButton(false, NEW_GAME[0], NEW_GAME[1], 1920, 1080);
        };

        assertEquals(0, launch(LoggingMode.DEVELOPMENT), harness.stderr());

        assertEquals(6, windowSystem.eventIterations, "only the window close request ends the run loop");
        assertEquals(5, windowSystem.uiGraphics.frames, "the main menu was shown until the close request");
        String log = harness.log();
        assertFalse(log.contains("Main menu Exit activated"), log);
        assertTrue(log.contains("Window close request received"), log);
        assertEquals(1, count(log, NORMAL_SHUTDOWN));
        List<String> tail = events.subList(events.size() - 3, events.size());
        assertEquals(List.of("ui-close", "destroyWindow", "terminate"), tail);
    }

    @Test
    void externalOverrideOfOneMainMenuResourceIsUsedAtStartup() {
        prepare();
        windowSystem.iterationsBeforeClose = 1;
        UiFixtures.resources().put("images/main-menu/background.png", UiFixtures.png(16, 9, 0x203040FF))
                .writeExternal(harness.configDirectory);

        assertEquals(0, launch(LoggingMode.DEVELOPMENT), harness.stderr());

        String log = harness.log();
        assertTrue(log.contains("UI resource 'images/main-menu/background.png' resolved from external override"), log);
        for (String bundled : List.of("definitions/main-menu.json", "images/main-menu/button-normal.png",
                "images/main-menu/button-hovered.png", "images/main-menu/button-pressed.png",
                "images/main-menu/button-disabled.png", "fonts/main-menu.ttf")) {
            assertTrue(log.contains("UI resource '" + bundled + "' resolved from bundled resources"), bundled);
        }
        assertEquals("external images/main-menu/background.png", drawnTextures().get(0));
    }
}
