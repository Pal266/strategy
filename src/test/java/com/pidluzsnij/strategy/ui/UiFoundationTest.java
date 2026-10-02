package com.pidluzsnij.strategy.ui;

import com.pidluzsnij.strategy.localization.Localization;
import com.pidluzsnij.strategy.testsupport.RecordingUiGraphics;
import com.pidluzsnij.strategy.testsupport.RecordingUiGraphics.ImageDraw;
import com.pidluzsnij.strategy.testsupport.RecordingUiGraphics.TextDraw;
import com.pidluzsnij.strategy.testsupport.UiFixtures;
import com.pidluzsnij.strategy.ui.definition.TextAlign;
import com.pidluzsnij.strategy.ui.definition.UiColor;
import com.pidluzsnij.strategy.ui.definition.UiDefinitionParser;
import com.pidluzsnij.strategy.ui.definition.VisualState;
import com.pidluzsnij.strategy.ui.input.PointerListener;
import com.pidluzsnij.strategy.ui.input.UiActivation;
import com.pidluzsnij.strategy.ui.layout.ScreenRect;
import com.pidluzsnij.strategy.ui.resource.UiResourcePath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.pidluzsnij.strategy.testsupport.UiFixtures.button;
import static com.pidluzsnij.strategy.testsupport.UiFixtures.definition;
import static com.pidluzsnij.strategy.testsupport.UiFixtures.image;
import static com.pidluzsnij.strategy.testsupport.UiFixtures.style;
import static com.pidluzsnij.strategy.testsupport.UiFixtures.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The UI foundation against a controlled rendering boundary: loading, rendering requests, localization, pointer
 * interaction, overrides and resource release, using only isolated fixture resources.
 */
class UiFoundationTest {

    static final String TITLE = "Заголовок — Příliš žluťoučký kůň, Árvíztűrő ŐŰ, ґанок";
    static final String START = "Почати гру";
    static final String LOCALIZATION = "test.title=" + TITLE + "\ntest.start=" + START + "\n";
    static final Set<String> BEHAVIORS = Set.of("test.start");

    static final byte[] RED = UiFixtures.png(4, 4, 0xFF0000FF);
    static final byte[] GREEN = UiFixtures.png(4, 4, 0x00FF00FF);
    static final byte[] BLUE = UiFixtures.png(4, 4, 0x0000FFFF);
    static final byte[] WHITE = UiFixtures.png(4, 4, 0xFFFFFFFF);

    /** Logical 1600×900: background, title text, start button with label. */
    static final String MAIN = definition(1600, 900,
            image("background", 0, 0, 1600, 900, "images/bg.png"),
            text("title", 200, 100, 1200, 100, style("test.title", "fonts/a.ttf", 48, "#FFEEDD", "center")),
            button("start", 600, 400, 400, 100, "test.start", "images/normal.png", "images/hovered.png",
                    "images/pressed.png", style("test.start", "fonts/b.ttf", 32, "#10203040", "left")));

    @TempDir
    Path temp;

    private final List<URLClassLoader> loaders = new ArrayList<>();
    private final RecordingUiGraphics graphics = new RecordingUiGraphics();
    private final Localization localization = UiFixtures.localization(LOCALIZATION);

    @AfterEach
    void closeLoaders() throws IOException {
        for (URLClassLoader loader : loaders) {
            loader.close();
        }
    }

    static UiFixtures.ResourceSet bundledSet() {
        return UiFixtures.resources()
                .put("screens/main.json", MAIN)
                .put("images/bg.png", RED)
                .put("images/normal.png", GREEN)
                .put("images/hovered.png", BLUE)
                .put("images/pressed.png", WHITE)
                .put("fonts/a.ttf", UiFixtures.font(UiFixtures.FONT_A))
                .put("fonts/b.ttf", UiFixtures.font(UiFixtures.FONT_B));
    }

    private Path configBase() {
        return temp.resolve("config");
    }

    private ClassLoader bundled(UiFixtures.ResourceSet set) {
        URLClassLoader loader = UiFixtures.classLoader(set.writeBundledDirectory(temp.resolve("classpath-" + loaders.size())));
        loaders.add(loader);
        return loader;
    }

    private UiFoundation initialize(UiFixtures.ResourceSet set, List<String> required, int width, int height)
            throws UiException {
        return UiFoundation.initialize(UiFixtures.configuration(bundled(set), configBase(), required, BEHAVIORS),
                graphics, localization, width, height);
    }

    private UiFoundation initialize(List<String> required) throws UiException {
        return initialize(bundledSet(), required, 1600, 900);
    }

    private static UiResourcePath path(String path) {
        return UiResourcePath.of(path);
    }

    // --- Rendering requests ----------------------------------------------------------------

    @Test
    void oneFrameProducesOrderedImageAndTextRequests() throws Exception {
        UiFoundation ui = initialize(List.of("screens/main.json"));
        UiScreen screen = ui.requiredScreen(path("screens/main.json")).orElseThrow();
        ui.show(screen, activation -> { });

        ui.render(3200, 1800);

        assertEquals(1, graphics.frames);
        assertEquals(List.of(
                new ImageDraw("bundled images/bg.png", new ScreenRect(0, 0, 3200, 1800)),
                new TextDraw(TITLE, "bundled fonts/a.ttf", 48, 96f, new ScreenRect(400, 200, 2400, 200),
                        TextAlign.CENTER, new UiColor(0xFF, 0xEE, 0xDD, 0xFF)),
                new ImageDraw("bundled images/normal.png", new ScreenRect(1200, 800, 800, 200)),
                new TextDraw(START, "bundled fonts/b.ttf", 32, 64f, new ScreenRect(1200, 800, 800, 200),
                        TextAlign.LEFT, new UiColor(0x10, 0x20, 0x30, 0x40))),
                graphics.draws);
        ui.close();
    }

    @Test
    void textIsRasterizedAtItsDisplayedSize() throws Exception {
        UiFoundation ui = initialize(bundledSet(), List.of("screens/main.json"), 3200, 1800);
        assertTrue(graphics.events.contains("ui-createTextFace bundled fonts/a.ttf 96"), graphics.events.toString());
        assertTrue(graphics.events.contains("ui-createTextFace bundled fonts/b.ttf 64"), graphics.events.toString());
        ui.close();
    }

    @Test
    void overlappingComponentsAreDrawnInDefinitionOrder() throws Exception {
        UiFixtures.ResourceSet set = bundledSet().put("screens/order.json", definition(100, 100,
                image("bottom", 0, 0, 80, 80, "images/bg.png"),
                image("middle", 10, 10, 80, 80, "images/normal.png"),
                image("top", 20, 20, 80, 80, "images/hovered.png")));
        UiFoundation ui = initialize(set, List.of("screens/order.json"), 100, 100);
        ui.show(ui.requiredScreen(path("screens/order.json")).orElseThrow(), a -> { });

        for (int frame = 0; frame < 3; frame++) {
            ui.render(100, 100);
        }

        List<String> order = graphics.draws.stream().map(d -> ((ImageDraw) d).texture()).toList();
        List<String> one = List.of("bundled images/bg.png", "bundled images/normal.png", "bundled images/hovered.png");
        List<String> expected = new ArrayList<>();
        for (int frame = 0; frame < 3; frame++) {
            expected.addAll(one);
        }
        assertEquals(expected, order);
        ui.close();
    }

    @Test
    void nothingIsSubmittedWhileNoScreenIsShown() throws Exception {
        UiFoundation ui = initialize(List.of("screens/main.json"));
        for (int frame = 0; frame < 5; frame++) {
            ui.render(1600, 900);
        }
        ui.pointerListener().primaryButton(true, 800, 450, 1600, 900);
        assertEquals(0, graphics.frames);
        assertEquals(List.of(), graphics.draws);
        assertTrue(ui.activeScreen().isEmpty());
        ui.close();
    }

    // --- Localization boundary and external presentation -----------------------------------

    @Test
    void displayedTextComesUnchangedFromLocalizationByKey() throws Exception {
        UiFoundation ui = initialize(List.of("screens/main.json"));
        UiScreen screen = ui.requiredScreen(path("screens/main.json")).orElseThrow();
        ui.show(screen, a -> { });
        ui.render(1600, 900);

        assertFalse(MAIN.contains(TITLE), "the definition holds the key, not the text");
        assertTrue(MAIN.contains("\"test.title\""));
        assertEquals(TITLE, screen.text("title").orElseThrow());
        List<String> texts = graphics.draws.stream().filter(TextDraw.class::isInstance)
                .map(d -> ((TextDraw) d).text()).toList();
        assertEquals(List.of(TITLE, START), texts);
        ui.close();
    }

    @Test
    void missingLocalizationValueShowsTheKeyAsLocalizationDefines() throws Exception {
        UiFixtures.ResourceSet set = bundledSet().put("screens/k.json", definition(100, 100,
                text("t", 0, 0, 100, 20, style("test.absent", "fonts/a.ttf", 10, "#FFFFFF", null))));
        UiFoundation ui = initialize(set, List.of("screens/k.json"), 100, 100);
        assertEquals(localization.text("test.absent"),
                ui.requiredScreen(path("screens/k.json")).orElseThrow().text("t").orElseThrow());
        ui.close();
    }

    @Test
    void fontSelectionAndSizeComeFromTheDefinition() throws Exception {
        UiFixtures.ResourceSet set = bundledSet()
                .put("screens/one.json", definition(100, 100,
                        text("label", 0, 0, 100, 20, style("test.start", "fonts/a.ttf", 12, "#FFFFFF", null))))
                .put("screens/two.json", definition(100, 100,
                        text("label", 0, 0, 100, 20, style("test.start", "fonts/b.ttf", 30, "#FFFFFF", null))));
        UiFoundation ui = initialize(set, List.of("screens/one.json", "screens/two.json"), 100, 100);

        ui.show(ui.requiredScreen(path("screens/one.json")).orElseThrow(), a -> { });
        ui.render(200, 200);
        ui.show(ui.requiredScreen(path("screens/two.json")).orElseThrow(), a -> { });
        ui.render(200, 200);

        TextDraw one = (TextDraw) graphics.draws.get(0);
        TextDraw two = (TextDraw) graphics.draws.get(1);
        assertEquals("bundled fonts/a.ttf", one.font());
        assertEquals(24f, one.pixelSize());
        assertEquals("bundled fonts/b.ttf", two.font());
        assertEquals(60f, two.pixelSize());
        ui.close();
    }

    @Test
    void twoResourceSetsProduceTheirOwnPresentationWithTheSameClasses() throws Exception {
        String alternative = definition(800, 600,
                button("start", 10, 20, 100, 50, "test.start", "images/pressed.png", "images/bg.png",
                        "images/normal.png", style("test.start", "fonts/a.ttf", 20, "#000000", "right")),
                image("background", 0, 0, 800, 600, "images/hovered.png"));
        UiFoundation first = initialize(List.of("screens/main.json"));
        first.show(first.requiredScreen(path("screens/main.json")).orElseThrow(), a -> { });
        first.render(1600, 900);
        List<RecordingUiGraphics.Draw> firstDraws = List.copyOf(graphics.draws);
        first.close();
        graphics.draws.clear();

        UiFoundation second = initialize(bundledSet().put("screens/main.json", alternative),
                List.of("screens/main.json"), 800, 600);
        second.show(second.requiredScreen(path("screens/main.json")).orElseThrow(), a -> { });
        second.render(800, 600);

        assertEquals(4, firstDraws.size());
        assertEquals(List.of(
                new ImageDraw("bundled images/pressed.png", new ScreenRect(10, 20, 100, 50)),
                new TextDraw(START, "bundled fonts/a.ttf", 20, 20f, new ScreenRect(10, 20, 100, 50), TextAlign.RIGHT,
                        new UiColor(0, 0, 0, 255)),
                new ImageDraw("bundled images/hovered.png", new ScreenRect(0, 0, 800, 600))), graphics.draws);
        second.close();
    }

    // --- Pointer interaction through the foundation -----------------------------------------

    private String renderButtonTexture(UiFoundation ui) {
        graphics.draws.clear();
        ui.render(1600, 900);
        return ((ImageDraw) graphics.draws.get(2)).texture();
    }

    @Test
    void visualStatesSelectTheExternallyDefinedStateImages() throws Exception {
        UiFoundation ui = initialize(List.of("screens/main.json"));
        UiScreen screen = ui.requiredScreen(path("screens/main.json")).orElseThrow();
        List<UiActivation> activations = new ArrayList<>();
        ui.show(screen, activations::add);
        PointerListener pointer = ui.pointerListener();

        pointer.pointerMoved(100, 100, 1600, 900);
        assertEquals(VisualState.NORMAL, screen.state("start"));
        assertEquals("bundled images/normal.png", renderButtonTexture(ui));

        pointer.pointerMoved(700, 450, 1600, 900);
        assertEquals(VisualState.HOVERED, screen.state("start"));
        assertEquals("bundled images/hovered.png", renderButtonTexture(ui));

        pointer.pointerMoved(100, 100, 1600, 900);
        assertEquals(VisualState.NORMAL, screen.state("start"));
        assertEquals("bundled images/normal.png", renderButtonTexture(ui));

        pointer.pointerMoved(700, 450, 1600, 900);
        pointer.primaryButton(true, 700, 450, 1600, 900);
        assertEquals(VisualState.PRESSED, screen.state("start"));
        assertEquals("bundled images/pressed.png", renderButtonTexture(ui));

        pointer.pointerMoved(100, 100, 1600, 900);
        assertEquals(VisualState.NORMAL, screen.state("start"));
        pointer.pointerMoved(700, 450, 1600, 900);
        assertEquals(VisualState.PRESSED, screen.state("start"));
        assertTrue(activations.isEmpty());

        pointer.primaryButton(false, 700, 450, 1600, 900);
        assertEquals(List.of(new UiActivation("start", "test.start")), activations);
        assertEquals(VisualState.HOVERED, screen.state("start"));
        ui.close();
    }

    @Test
    void releaseOutsideAndForeignPressesDoNotActivate() throws Exception {
        UiFoundation ui = initialize(List.of("screens/main.json"));
        UiScreen screen = ui.requiredScreen(path("screens/main.json")).orElseThrow();
        List<UiActivation> activations = new ArrayList<>();
        ui.show(screen, activations::add);
        PointerListener pointer = ui.pointerListener();

        pointer.primaryButton(true, 700, 450, 1600, 900);
        pointer.pointerMoved(100, 100, 1600, 900);
        pointer.primaryButton(false, 100, 100, 1600, 900);
        assertEquals(VisualState.NORMAL, screen.state("start"));

        pointer.primaryButton(true, 100, 100, 1600, 900);
        pointer.pointerMoved(700, 450, 1600, 900);
        pointer.primaryButton(false, 700, 450, 1600, 900);

        assertEquals(List.of(), activations);
        ui.close();
    }

    @Test
    void pointerInTheUnusedFramebufferAreaDoesNotInteract() throws Exception {
        UiFixtures.ResourceSet set = bundledSet().put("screens/edge.json", definition(1600, 900,
                button("edge", -100, 0, 300, 900, "test.start", "images/normal.png", "images/hovered.png",
                        "images/pressed.png", null)));
        UiFoundation ui = initialize(set, List.of("screens/edge.json"), 2400, 900);
        UiScreen screen = ui.requiredScreen(path("screens/edge.json")).orElseThrow();
        List<UiActivation> activations = new ArrayList<>();
        ui.show(screen, activations::add);
        PointerListener pointer = ui.pointerListener();

        // A 2400×900 framebuffer leaves 400-pixel unused columns; the button is drawn from x=300.
        pointer.pointerMoved(350, 450, 2400, 900);
        assertEquals(VisualState.NORMAL, screen.state("edge"));
        pointer.primaryButton(true, 350, 450, 2400, 900);
        assertEquals(VisualState.NORMAL, screen.state("edge"));
        pointer.primaryButton(false, 350, 450, 2400, 900);
        assertEquals(VisualState.NORMAL, screen.state("edge"));
        assertEquals(List.of(), activations);

        ui.render(2400, 900);
        assertEquals(new ScreenRect(400, 0, 1600, 900), graphics.visibleArea,
                "drawing is clipped to the logical area, so the visible part is the interactive part");
        ui.close();
    }

    // --- Overrides --------------------------------------------------------------------------

    @Test
    void externalOverridesReplaceOnlyTheOverriddenResources() throws Exception {
        UiFixtures.resources().put("images/hovered.png", RED).put("fonts/b.ttf", UiFixtures.font(UiFixtures.FONT_A))
                .writeExternal(configBase());
        UiFoundation ui = initialize(List.of("screens/main.json"));

        assertTrue(graphics.events.contains("ui-createTexture external images/hovered.png"), graphics.events.toString());
        assertTrue(graphics.events.contains("ui-createTexture bundled images/normal.png"));
        assertTrue(graphics.events.contains("ui-createTextFace external fonts/b.ttf 32"), graphics.events.toString());
        assertTrue(graphics.events.contains("ui-createTextFace bundled fonts/a.ttf 48"));
        ui.close();
    }

    @Test
    void externalDefinitionOverrideIsUsed() throws Exception {
        UiFixtures.resources().put("screens/main.json", definition(100, 100,
                image("only", 0, 0, 10, 10, "images/bg.png"))).writeExternal(configBase());
        UiFoundation ui = initialize(List.of("screens/main.json"));
        assertEquals(List.of("only"), ui.requiredScreen(path("screens/main.json")).orElseThrow()
                .definition().components().stream().map(c -> c.id()).toList());
        ui.close();
    }

    enum Invalid { UNREADABLE, MALFORMED, UNSUPPORTED, INVALID_FOR_TYPE }

    private static byte[] invalid(String kind, Invalid invalid) {
        return switch (kind) {
            case "image" -> switch (invalid) {
                case MALFORMED -> UiFixtures.malformedPng();
                case UNSUPPORTED -> UiFixtures.gif();
                case INVALID_FOR_TYPE -> UiFixtures.font(UiFixtures.FONT_A);
                case UNREADABLE -> throw new IllegalArgumentException();
            };
            case "font" -> switch (invalid) {
                case MALFORMED -> UiFixtures.truncatedFont();
                case UNSUPPORTED -> UiFixtures.openTypeCff();
                case INVALID_FOR_TYPE -> RED;
                case UNREADABLE -> throw new IllegalArgumentException();
            };
            default -> switch (invalid) {
                case MALFORMED -> "{\"layout\": ".getBytes(StandardCharsets.UTF_8);
                case UNSUPPORTED -> definition(100, 100, "{\"id\": \"x\", \"type\": \"script\", \"x\": 0, \"y\": 0, "
                        + "\"width\": 1, \"height\": 1}").getBytes(StandardCharsets.UTF_8);
                case INVALID_FOR_TYPE -> RED;
                case UNREADABLE -> throw new IllegalArgumentException();
            };
        };
    }

    @ParameterizedTest
    @EnumSource(Invalid.class)
    void invalidExistingOverridesFailWithoutBundledSubstitution(Invalid invalid) throws Exception {
        Map<String, String> paths = Map.of("image", "images/hovered.png", "font", "fonts/b.ttf",
                "definition", "screens/main.json");
        Map<String, String> stages = Map.of("image", "decode UI image", "font", "load UI font",
                "definition", UiDefinitionParser.STAGE);
        for (String kind : List.of("image", "font", "definition")) {
            Path external = UiFixtures.externalRoot(configBase());
            if (Files.exists(external)) {
                try (var files = Files.walk(external)) {
                    for (Path file : files.sorted(java.util.Comparator.reverseOrder()).toList()) {
                        Files.delete(file);
                    }
                }
            }
            String relative = paths.get(kind);
            if (invalid == Invalid.UNREADABLE) {
                Files.createDirectories(external.resolve(relative));
            } else {
                UiFixtures.resources().put(relative, invalid(kind, invalid)).writeExternal(configBase());
            }
            RecordingUiGraphics fresh = new RecordingUiGraphics();
            UiException failure = assertThrows(UiException.class, () -> UiFoundation.initialize(
                    UiFixtures.configuration(bundled(bundledSet()), configBase(), List.of("screens/main.json"), BEHAVIORS),
                    fresh, localization, 1600, 900), kind + " " + invalid);

            assertEquals("external " + relative, failure.resource(), kind + " " + invalid);
            if (invalid != Invalid.UNREADABLE) {
                assertEquals(stages.get(kind), failure.stage(), kind + " " + invalid);
            }
            assertFalse(fresh.events.contains("ui-createTexture bundled " + relative), "no bundled substitution");
            assertFalse(fresh.events.stream().anyMatch(e -> e.startsWith("ui-createTextFace bundled " + relative)));
            assertTrue(fresh.everythingReleasedOnce(), "no partial result remains allocated");
            assertEquals(1, fresh.closes);
        }
    }

    @Test
    void missingRequiredResourceFailsInitializationWithoutPartialUi() throws Exception {
        UiFixtures.ResourceSet set = bundledSet().put("screens/broken.json", definition(100, 100,
                image("ok", 0, 0, 10, 10, "images/bg.png"),
                image("missing", 0, 0, 10, 10, "images/missing.png")));

        UiException failure = assertThrows(UiException.class,
                () -> initialize(set, List.of("screens/main.json", "screens/broken.json"), 100, 100));

        assertEquals("resolve UI resource", failure.stage());
        assertEquals("images/missing.png", failure.resource());
        assertTrue(graphics.textures.size() >= 2, "resources were allocated before the failure");
        assertTrue(graphics.everythingReleasedOnce());
        assertEquals(1, graphics.closes);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/SENTINEL.png", "../SENTINEL.png", "images/../../SENTINEL.png", "C:/SENTINEL.png",
            "\\\\\\\\host\\\\SENTINEL.png", "images\\\\..\\\\..\\\\SENTINEL.png", "./../SENTINEL.png"})
    void escapingResourceReferencesAreRejectedBeforeAnyResourceIsRead(String reference) throws Exception {
        byte[] sentinel = RED;
        Files.createDirectories(temp.resolve("config/strategy"));
        Files.write(temp.resolve("SENTINEL.png"), sentinel);
        Files.write(temp.resolve("config/strategy/SENTINEL.png"), sentinel);
        Files.write(temp.resolve("config/SENTINEL.png"), sentinel);
        UiFixtures.ResourceSet set = bundledSet().put("screens/escape.json", definition(100, 100,
                image("escape", 0, 0, 10, 10, reference)));

        UiException failure = assertThrows(UiException.class, () -> initialize(set, List.of("screens/escape.json"), 100, 100));

        assertEquals(UiDefinitionParser.STAGE, failure.stage(), "rejected while validating the definition");
        assertTrue(failure.reason().contains("invalid resource reference"), failure.reason());
        assertTrue(graphics.textures.isEmpty(), "no image was read");
    }

    @Test
    void escapingDefinitionPathsCannotBeRequested() {
        for (String path : List.of("../x.json", "/x.json", "C:\\x.json")) {
            assertThrows(IllegalArgumentException.class, () -> UiResourcePath.of(path));
        }
    }

    // --- Read-only inputs -------------------------------------------------------------------

    @Test
    void uiLoadingNeverModifiesItsInputsOrTheSharedApplicationFiles() throws Exception {
        Path strategy = configBase().resolve("strategy");
        Files.createDirectories(strategy);
        Files.writeString(strategy.resolve("application-settings.toml"), "[video]\nfullscreen = false\n");
        Files.writeString(strategy.resolve("log.log"), "existing log\n");
        UiFixtures.resources().put("images/hovered.png", RED).writeExternal(configBase());
        ClassLoader loader = bundled(bundledSet());
        Path bundledRoot = temp.resolve("classpath-0");
        Map<String, String> before = UiFixtures.snapshot(strategy, bundledRoot);

        UiFoundation ui = UiFoundation.initialize(UiFixtures.configuration(loader, configBase(),
                List.of("screens/main.json"), BEHAVIORS), graphics, localization, 1600, 900);
        ui.close();
        UiFixtures.resources().put("images/normal.png", UiFixtures.gif()).writeExternal(configBase());
        Map<String, String> beforeFailure = UiFixtures.snapshot(strategy, bundledRoot);
        assertThrows(UiException.class, () -> UiFoundation.initialize(UiFixtures.configuration(loader, configBase(),
                List.of("screens/main.json"), BEHAVIORS), new RecordingUiGraphics(), localization, 1600, 900));

        Map<String, String> after = UiFixtures.snapshot(strategy, bundledRoot);
        assertEquals(beforeFailure, after);
        before.keySet().forEach(key -> {
            if (!key.endsWith("normal.png")) {
                assertEquals(before.get(key), after.get(key), key);
            }
        });
        assertEquals(before.size() + 1, after.size(), "only the file this test added appeared");
    }

    // --- Release ----------------------------------------------------------------------------

    @Test
    void successfulCloseReleasesEveryResourceOnce() throws Exception {
        UiFoundation ui = initialize(List.of("screens/main.json"));
        UiScreen screen = ui.requiredScreen(path("screens/main.json")).orElseThrow();
        ui.close();
        ui.close();

        assertEquals(4, graphics.textures.size());
        assertEquals(2, graphics.faces.size());
        assertTrue(graphics.everythingReleasedOnce());
        assertEquals(1, graphics.closes);
        assertTrue(screen.isReleased());
        ui.render(1600, 900);
        assertEquals(0, graphics.frames);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3})
    void textureFailureAfterSomeAllocationsReleasesThemOnce(int failAt) {
        graphics.failTextureAt = failAt;
        UiException failure = assertThrows(UiException.class, () -> initialize(List.of("screens/main.json")));
        assertEquals("create UI texture", failure.stage());
        assertEquals(failAt, graphics.textures.size());
        assertTrue(graphics.everythingReleasedOnce());
        assertEquals(1, graphics.closes);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void textFaceFailureAfterSomeAllocationsReleasesEverythingOnce(int failAt) {
        graphics.failFaceAt = failAt;
        assertThrows(UiException.class, () -> initialize(List.of("screens/main.json")));
        assertEquals(4, graphics.textures.size());
        assertEquals(failAt, graphics.faces.size());
        assertTrue(graphics.everythingReleasedOnce());
        assertEquals(1, graphics.closes);
    }

    @Test
    void fontFailureReleasesEarlierAllocations() throws Exception {
        UiFixtures.resources().put("fonts/b.ttf", UiFixtures.truncatedFont()).writeExternal(configBase());
        assertThrows(UiException.class, () -> initialize(List.of("screens/main.json")));
        assertEquals(4, graphics.textures.size());
        assertTrue(graphics.everythingReleasedOnce());
    }

    @Test
    void secondDefinitionFailureReleasesTheFirstDefinitionToo() {
        UiFixtures.ResourceSet set = bundledSet().put("screens/bad.json", "not json");
        assertThrows(UiException.class, () -> initialize(set, List.of("screens/main.json", "screens/bad.json"), 1600, 900));
        assertEquals(4, graphics.textures.size());
        assertTrue(graphics.everythingReleasedOnce());
        assertEquals(1, graphics.closes);
    }

    @Test
    void renderingInitializationFailureReleasesSafely() {
        graphics.initializeFailure = new IllegalStateException("shader probe");
        UiException failure = assertThrows(UiException.class, () -> initialize(List.of("screens/main.json")));
        assertEquals("initialize UI rendering", failure.stage());
        assertInstanceOf(IllegalStateException.class, failure.getCause());
        assertTrue(graphics.textures.isEmpty());
        assertEquals(1, graphics.closes);
    }

    @Test
    void releaseFailureDoesNotSuppressLaterReleases() throws Exception {
        UiFoundation ui = initialize(List.of("screens/main.json"));
        graphics.failTextureReleaseFor = "bundled images/bg.png";
        graphics.closeFailure = new IllegalStateException("graphics close probe");

        UiCleanupException failure = assertThrows(UiCleanupException.class, ui::close);

        assertEquals(2, failure.getSuppressed().length);
        assertTrue(graphics.textures.stream().allMatch(t -> t.releases == 1));
        assertTrue(graphics.faces.stream().allMatch(f -> f.releases == 1));
        assertEquals(1, graphics.closes);
        ui.close();
        assertEquals(1, graphics.closes, "later calls do nothing");
    }

    @Test
    void loadAfterStartupIsAllOrNothingToo() throws Exception {
        UiFoundation ui = initialize(List.of());
        UiScreen screen = ui.load(path("screens/main.json"));
        assertEquals(3, screen.definition().components().size());
        graphics.failFaceAt = graphics.faces.size() + 1;
        assertThrows(UiException.class, () -> ui.load(path("screens/main.json")));
        assertTrue(graphics.textures.subList(4, graphics.textures.size()).stream().allMatch(t -> t.releases == 1));
        ui.close();
        assertTrue(graphics.everythingReleasedOnce());
    }

    @Test
    void screensOfAnotherFoundationCannotBeShown() throws Exception {
        UiFoundation first = initialize(List.of("screens/main.json"));
        UiFoundation second = initialize(List.of());
        UiScreen screen = first.requiredScreen(path("screens/main.json")).orElseThrow();
        assertThrows(IllegalArgumentException.class, () -> second.show(screen, a -> { }));
        first.close();
        second.close();
    }
}
