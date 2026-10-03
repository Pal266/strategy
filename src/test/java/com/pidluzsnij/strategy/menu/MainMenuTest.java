package com.pidluzsnij.strategy.menu;

import com.pidluzsnij.strategy.localization.Localization;
import com.pidluzsnij.strategy.localization.LocalizationInitializer;
import com.pidluzsnij.strategy.localization.LocalizationResources;
import com.pidluzsnij.strategy.testsupport.RecordingUiGraphics;
import com.pidluzsnij.strategy.testsupport.UiFixtures;
import com.pidluzsnij.strategy.ui.UiException;
import com.pidluzsnij.strategy.ui.UiFoundation;
import com.pidluzsnij.strategy.ui.UiScreen;
import com.pidluzsnij.strategy.ui.UiStartupConfiguration;
import com.pidluzsnij.strategy.ui.asset.UiFont;
import com.pidluzsnij.strategy.ui.definition.Bounds;
import com.pidluzsnij.strategy.ui.definition.TextStyle;
import com.pidluzsnij.strategy.ui.definition.UiComponent;
import com.pidluzsnij.strategy.ui.definition.UiDefinition;
import com.pidluzsnij.strategy.ui.definition.UiDefinitionParser;
import com.pidluzsnij.strategy.ui.definition.VisualState;
import com.pidluzsnij.strategy.ui.input.PointerListener;
import com.pidluzsnij.strategy.ui.input.UiActivation;
import com.pidluzsnij.strategy.ui.resource.ResolvedUiResource;
import com.pidluzsnij.strategy.ui.resource.UiResourceOrigin;
import com.pidluzsnij.strategy.ui.resource.UiResourcePath;
import com.pidluzsnij.strategy.ui.resource.UiResources;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The SPEC007 main menu built from the bundled production resources: supplied resources, layout, localized
 * labels, font coverage, disabled and enabled button behavior and external overrides. Every test uses an
 * isolated per-user configuration location, so the user's own external UI directory is never consulted.
 */
class MainMenuTest {

    /** SHA-256 of the supplied SPEC007 resources (from the package's SHA256SUMS.txt). */
    static final Map<String, String> SUPPLIED = new LinkedHashMap<>();

    static {
        SUPPLIED.put("definitions/main-menu.json", "2427e0ca542343be19fca7e81916dd94e7793ef0e7af7308fa747d610f3fc472");
        SUPPLIED.put("fonts/main-menu.ttf", "9d7583b7dc9e812afd32a14280c5cac3160012efe50c8d08938f4fea266ff67f");
        SUPPLIED.put("images/main-menu/background.png", "f71ceb3f819fe4f9a9000b8fdeb2a6a56485a45ff29a8c6ac650839814f650a0");
        SUPPLIED.put("images/main-menu/button-disabled.png", "807a8682db025fef68279a29023056041cfb3553e021397a08ed65b36284a03b");
        SUPPLIED.put("images/main-menu/button-hovered.png", "e37a72412e341766f468c17972c6e5ce2acfd46a2a7ca398b10e13a7d9456775");
        SUPPLIED.put("images/main-menu/button-normal.png", "05d49c575a5ef8e8d503fcab4383c3085c4b51724139916f2a405b008b55d8b3");
        SUPPLIED.put("images/main-menu/button-pressed.png", "afb3bdf7aa7240b15fae0c59005cb2d46d6bd87040c86cb235ca7fc6163dc970");
    }

    /** The SPEC007 localization table: key → translations in English, Ukrainian, Czech, Hungarian order. */
    static final Map<String, List<String>> LABELS = new LinkedHashMap<>();

    static {
        LABELS.put(MainMenu.NEW_GAME, List.of("New Game", "Нова гра", "Nová hra", "Új játék"));
        LABELS.put(MainMenu.LOAD_GAME, List.of("Load Game", "Завантажити гру", "Načíst hru", "Játék betöltése"));
        LABELS.put(MainMenu.SETTINGS, List.of("Settings", "Налаштування", "Nastavení", "Beállítások"));
        LABELS.put(MainMenu.EXIT, List.of("Exit", "Вийти", "Konec", "Kilépés"));
    }

    static final List<String> LANGUAGES = List.of("en", "uk", "cs", "hu");
    /** Menu buttons in their required top-to-bottom order. */
    static final List<String> BUTTONS = List.of("newGame", "loadGame", "settings", "exit");
    static final List<String> DISABLED = List.of("newGame", "loadGame", "settings");

    /** A 1920×1080 framebuffer shows the 1920×1080 logical canvas at scale 1. */
    static final int W = 1920;
    static final int H = 1080;

    @TempDir
    Path temp;

    private final RecordingUiGraphics graphics = new RecordingUiGraphics();
    private final List<UiActivation> activations = new ArrayList<>();
    private UiFoundation ui;

    @AfterEach
    void close() {
        if (ui != null) {
            ui.close();
        }
    }

    private Path configBase() {
        return temp.resolve("config");
    }

    private static Localization localization(String language) {
        return new LocalizationInitializer(LocalizationResources.bundled()).initialize(language).orElseThrow();
    }

    /** Initializes the production main-menu UI configuration with an isolated per-user location. */
    private UiScreen showMainMenu(String language) throws UiException {
        UiStartupConfiguration configuration = MainMenu.uiConfiguration(this::configBase);
        ui = UiFoundation.initialize(configuration, graphics, localization(language), W, H);
        UiScreen screen = ui.requiredScreen(MainMenu.DEFINITION).orElseThrow();
        ui.show(screen, activations::add);
        return screen;
    }

    private static UiDefinition suppliedDefinition() throws UiException {
        return new UiDefinitionParser(MainMenu.BEHAVIORS).parse(bundled(MainMenu.DEFINITION.value()));
    }

    private static ResolvedUiResource bundled(String path) throws UiException {
        return new UiResources(MainMenu.class.getClassLoader(), Path.of("no-external-ui-root"))
                .resolve(UiResourcePath.of(path));
    }

    private static double[] center(UiScreen screen, String id) {
        Bounds b = screen.definition().component(id).bounds();
        return new double[] {b.x() + b.width() / 2.0, b.y() + b.height() / 2.0};
    }

    // --- Production configuration and supplied resources ---------------------------------------

    @Test
    void productionConfigurationRequiresTheMainMenuWithItsBehaviors() {
        UiStartupConfiguration production = MainMenu.uiConfiguration(this::configBase);

        assertEquals(List.of(UiResourcePath.of("definitions/main-menu.json")), production.requiredDefinitions());
        assertEquals(Set.of("menu.new_game", "menu.load_game", "menu.settings", "menu.exit"),
                production.supportedBehaviors());
        assertEquals(MainMenu.class.getClassLoader(), production.bundledResources());
    }

    @Test
    void suppliedResourcesAreBundledUnchangedAndResolveThroughTheUiResourceModel() throws Exception {
        UiDefinition definition = suppliedDefinition();
        Set<String> referenced = new TreeSet<>();
        definition.imageResources().forEach(path -> referenced.add(path.value()));
        definition.textStyles().forEach(style -> referenced.add(style.font().value()));
        referenced.add(MainMenu.DEFINITION.value());

        assertEquals(new TreeSet<>(SUPPLIED.keySet()), referenced, "the definition references the supplied resources");
        for (Map.Entry<String, String> supplied : SUPPLIED.entrySet()) {
            ResolvedUiResource resource = bundled(supplied.getKey());
            assertEquals(UiResourceOrigin.BUNDLED, resource.origin(), supplied.getKey());
            assertEquals(supplied.getValue(), UiFixtures.sha256(resource.bytes()), supplied.getKey() + " is unchanged");
        }
    }

    @Test
    void backgroundFillsTheLogicalCanvasBehindTheMenuControls() throws Exception {
        UiDefinition definition = suppliedDefinition();

        assertEquals(1920, definition.logicalWidth());
        assertEquals(1080, definition.logicalHeight());
        UiComponent first = definition.components().get(0);
        assertEquals(new UiComponent.Image("background", new Bounds(0, 0, 1920, 1080),
                UiResourcePath.of("images/main-menu/background.png"), false), first, "first, so drawn behind");
        assertFalse(first.blocking(), "the background is a non-blocking decoration");
        assertEquals(1, definition.components().stream().filter(c -> c instanceof UiComponent.Image).count());
    }

    @Test
    void fourButtonsAreArrangedVerticallyInTheRequiredOrder() throws Exception {
        UiDefinition definition = suppliedDefinition();
        List<UiComponent.Button> buttons = definition.components().stream()
                .filter(c -> c instanceof UiComponent.Button).map(c -> (UiComponent.Button) c).toList();

        assertEquals(BUTTONS, buttons.stream().map(UiComponent.Button::id).toList());
        assertEquals(List.of(MainMenu.NEW_GAME, MainMenu.LOAD_GAME, MainMenu.SETTINGS, MainMenu.EXIT),
                buttons.stream().map(UiComponent.Button::behavior).toList());
        for (int i = 1; i < buttons.size(); i++) {
            Bounds above = buttons.get(i - 1).bounds();
            Bounds below = buttons.get(i).bounds();
            assertEquals(above.x(), below.x(), "vertically aligned");
            assertTrue(above.y() + above.height() <= below.y(), "stacked top to bottom without overlap");
        }
        assertEquals(List.of(false, false, false, true), buttons.stream().map(UiComponent.Button::enabled).toList());
        for (UiComponent.Button button : buttons) {
            assertEquals(UiResourcePath.of("images/main-menu/button-disabled.png"), button.image(VisualState.DISABLED));
            assertEquals(UiResourcePath.of("images/main-menu/button-normal.png"), button.image(VisualState.NORMAL));
            assertEquals(UiResourcePath.of("images/main-menu/button-hovered.png"), button.image(VisualState.HOVERED));
            assertEquals(UiResourcePath.of("images/main-menu/button-pressed.png"), button.image(VisualState.PRESSED));
        }
    }

    @Test
    void definitionIsDeclarativeDataWithApplicationOwnedBehavior() throws Exception {
        UiDefinition definition = suppliedDefinition();

        // The closed schema accepted every member, so the definition holds only supported presentation data.
        Set<String> behaviors = new TreeSet<>();
        definition.components().forEach(c -> {
            if (c instanceof UiComponent.Button button) {
                behaviors.add(button.behavior());
            }
        });
        assertEquals(new TreeSet<>(MainMenu.BEHAVIORS), behaviors, "only semantic identifiers");
        UiException unsupported = assertThrows(UiException.class,
                () -> new UiDefinitionParser(Set.of()).parse(bundled(MainMenu.DEFINITION.value())),
                "identifiers are meaningful only when application code declares them");
        assertTrue(unsupported.reason().contains("unsupported behavior"), unsupported.reason());
    }

    @ParameterizedTest
    @ValueSource(strings = {"script", "class", "method", "expression", "onClick", "nativeLibrary"})
    void suppliedDefinitionWithAnUnknownMemberIsRejected(String member) throws Exception {
        // The closed schema, not a keyword search, keeps executable constructs out of UI resources.
        String json = new String(bundled(MainMenu.DEFINITION.value()).bytes(), StandardCharsets.UTF_8)
                .replace("\"behavior\": \"menu.exit\",", "\"behavior\": \"menu.exit\", \"" + member + "\": \"x\",");
        UiException failure = assertThrows(UiException.class, () -> new UiDefinitionParser(MainMenu.BEHAVIORS).parse(
                new ResolvedUiResource(MainMenu.DEFINITION, UiResourceOrigin.BUNDLED, json.getBytes(StandardCharsets.UTF_8))));
        assertTrue(failure.reason().contains("unsupported member '" + member + "'"), failure.reason());
    }

    // --- Font and localization -------------------------------------------------------------

    @Test
    void suppliedFontCoversEveryCharacterOfTheFourTranslations() throws Exception {
        ResolvedUiResource resource = bundled("fonts/main-menu.ttf");
        assertEquals(SUPPLIED.get("fonts/main-menu.ttf"), UiFixtures.sha256(resource.bytes()), "the supplied Noto Serif");
        try (UiFont font = UiFont.load(resource)) {
            for (List<String> translations : LABELS.values()) {
                for (String translation : translations) {
                    translation.codePoints().forEach(codePoint -> assertTrue(font.hasGlyph(codePoint),
                            "glyph for U+" + Integer.toHexString(codePoint).toUpperCase(java.util.Locale.ROOT)));
                }
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"en", "uk", "cs", "hu"})
    void labelsUseTheSpecifiedKeysAndTranslations(String language) throws Exception {
        UiScreen screen = showMainMenu(language);
        int column = LANGUAGES.indexOf(language);

        List<String> keys = new ArrayList<>(LABELS.keySet());
        for (int i = 0; i < BUTTONS.size(); i++) {
            UiComponent.Button button = (UiComponent.Button) screen.definition().component(BUTTONS.get(i));
            TextStyle label = button.label().orElseThrow();
            assertEquals(keys.get(i), label.localizationKey(), BUTTONS.get(i));
            assertEquals(UiResourcePath.of("fonts/main-menu.ttf"), label.font());
            assertEquals(LABELS.get(keys.get(i)).get(column), screen.text(BUTTONS.get(i)).orElseThrow(),
                    language + " " + keys.get(i));
        }

        ui.render(W, H);
        List<String> drawnTexts = graphics.draws.stream()
                .filter(d -> d instanceof RecordingUiGraphics.TextDraw)
                .map(d -> ((RecordingUiGraphics.TextDraw) d).text()).toList();
        assertEquals(LABELS.values().stream().map(t -> t.get(column)).toList(), drawnTexts,
                "the renderer receives the translations unchanged, in button order");
    }

    // --- Rendering and interaction ---------------------------------------------------------

    @Test
    void backgroundIsDrawnFirstOverTheWholeCanvasAndDisabledButtonsUseTheDisabledArtwork() throws Exception {
        showMainMenu("en");

        ui.render(W, H);

        List<String> images = graphics.draws.stream()
                .filter(d -> d instanceof RecordingUiGraphics.ImageDraw)
                .map(d -> ((RecordingUiGraphics.ImageDraw) d).texture()).toList();
        assertEquals(List.of("bundled images/main-menu/background.png",
                "bundled images/main-menu/button-disabled.png",
                "bundled images/main-menu/button-disabled.png",
                "bundled images/main-menu/button-disabled.png",
                "bundled images/main-menu/button-normal.png"), images);
        RecordingUiGraphics.ImageDraw background = (RecordingUiGraphics.ImageDraw) graphics.draws.get(0);
        assertEquals(0f, background.rect().x());
        assertEquals(0f, background.rect().y());
        assertEquals(1920f, background.rect().width());
        assertEquals(1080f, background.rect().height());
    }

    @Test
    void disabledButtonsStayDisabledAndNeverEmitTheirRecognizedBehaviors() throws Exception {
        UiScreen screen = showMainMenu("en");
        PointerListener pointer = ui.pointerListener();

        for (String id : DISABLED) {
            double[] inside = center(screen, id);
            assertEquals(VisualState.DISABLED, screen.state(id));
            pointer.pointerMoved(inside[0], inside[1], W, H);
            assertEquals(VisualState.DISABLED, screen.state(id), id + " is not hovered");
            pointer.primaryButton(true, inside[0], inside[1], W, H);
            assertEquals(VisualState.DISABLED, screen.state(id), id + " is not pressed");
            pointer.pointerMoved(5, 5, W, H);
            assertEquals(VisualState.DISABLED, screen.state(id));
            pointer.pointerMoved(inside[0], inside[1], W, H);
            assertEquals(VisualState.DISABLED, screen.state(id));
            pointer.primaryButton(false, inside[0], inside[1], W, H);
            assertEquals(VisualState.DISABLED, screen.state(id));
        }
        assertEquals(List.of(), activations, "no semantic activation from a disabled button");
        assertEquals(VisualState.NORMAL, screen.state("exit"), "the enabled Exit button was never involved");
    }

    @Test
    void exitUsesItsInteractiveStatesAndActivatesOnceAfterAValidClick() throws Exception {
        UiScreen screen = showMainMenu("en");
        PointerListener pointer = ui.pointerListener();
        double[] exit = center(screen, "exit");

        assertEquals(VisualState.NORMAL, screen.state("exit"));
        pointer.pointerMoved(exit[0], exit[1], W, H);
        assertEquals(VisualState.HOVERED, screen.state("exit"));
        ui.render(W, H);
        assertTrue(graphics.draws.contains(new RecordingUiGraphics.ImageDraw("bundled images/main-menu/button-hovered.png",
                new com.pidluzsnij.strategy.ui.layout.ScreenRect(640, 750, 640, 120))));

        pointer.primaryButton(true, exit[0], exit[1], W, H);
        assertEquals(VisualState.PRESSED, screen.state("exit"));
        assertEquals(List.of(), activations, "nothing happens on press");
        pointer.pointerMoved(5, 5, W, H);
        assertEquals(VisualState.NORMAL, screen.state("exit"));
        pointer.pointerMoved(exit[0], exit[1], W, H);
        assertEquals(VisualState.PRESSED, screen.state("exit"));
        graphics.draws.clear();
        ui.render(W, H);
        assertTrue(graphics.draws.stream().anyMatch(d -> d instanceof RecordingUiGraphics.ImageDraw image
                && image.texture().equals("bundled images/main-menu/button-pressed.png")));

        pointer.primaryButton(false, exit[0], exit[1], W, H);
        assertEquals(List.of(new UiActivation("exit", MainMenu.EXIT)), activations);
        assertEquals(VisualState.HOVERED, screen.state("exit"));

        // A release that follows a press outside, or a release outside after pressing Exit, does not activate.
        pointer.primaryButton(true, 5, 5, W, H);
        pointer.primaryButton(false, exit[0], exit[1], W, H);
        pointer.primaryButton(true, exit[0], exit[1], W, H);
        pointer.primaryButton(false, 5, 5, W, H);
        assertEquals(1, activations.size(), "exactly one activation");
    }

    @Test
    void onlyExitRequestsShutdownWhenShownByApplicationCode() throws Exception {
        ui = UiFoundation.initialize(MainMenu.uiConfiguration(this::configBase), graphics, localization("en"), W, H);
        AtomicInteger exits = new AtomicInteger();

        UiScreen screen = MainMenu.show(ui, exits::incrementAndGet);

        assertEquals(screen, ui.activeScreen().orElseThrow(), "the main menu is the active screen");
        PointerListener pointer = ui.pointerListener();
        for (String id : BUTTONS) {
            double[] p = center(screen, id);
            pointer.pointerMoved(p[0], p[1], W, H);
            pointer.primaryButton(true, p[0], p[1], W, H);
            pointer.primaryButton(false, p[0], p[1], W, H);
        }
        assertEquals(1, exits.get(), "Exit requests shutdown once; disabled buttons request nothing");
    }

    @Test
    void showingTheMainMenuFailsWhenTheUiDidNotLoadIt() throws Exception {
        UiStartupConfiguration none = new UiStartupConfiguration(MainMenu.class.getClassLoader(), this::configBase,
                List.of(), MainMenu.BEHAVIORS);
        ui = UiFoundation.initialize(none, graphics, localization("en"), W, H);

        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> MainMenu.show(ui, () -> { }));
        assertTrue(failure.getMessage().contains("definitions/main-menu.json"), failure.getMessage());
        assertTrue(ui.activeScreen().isEmpty());
    }

    @Test
    void productionStartupPairsTheMainMenuConfigurationWithTheMainMenu() throws Exception {
        com.pidluzsnij.strategy.ui.UiStartup startup = MainMenu.uiStartup(this::configBase);
        assertEquals(List.of(MainMenu.DEFINITION), startup.configuration().requiredDefinitions());

        ui = UiFoundation.initialize(startup.configuration(), graphics, localization("en"), W, H);
        AtomicInteger exits = new AtomicInteger();
        startup.firstScreen().show(ui, exits::incrementAndGet);

        assertEquals(ui.requiredScreen(MainMenu.DEFINITION), ui.activeScreen());
    }

    // --- External overrides --------------------------------------------------------------------

    @Test
    void externalOverrideReplacesOnlyTheOverriddenMainMenuResource() throws Exception {
        byte[] override = UiFixtures.png(640, 120, 0xFF00FFFF);
        UiFixtures.resources().put("images/main-menu/button-disabled.png", override).writeExternal(configBase());

        showMainMenu("en");

        List<String> created = graphics.events.stream().filter(e -> e.startsWith("ui-createTexture ")).toList();
        assertEquals(List.of("ui-createTexture bundled images/main-menu/background.png",
                "ui-createTexture bundled images/main-menu/button-normal.png",
                "ui-createTexture bundled images/main-menu/button-hovered.png",
                "ui-createTexture bundled images/main-menu/button-pressed.png",
                "ui-createTexture external images/main-menu/button-disabled.png"), created);
        assertTrue(graphics.events.contains("ui-createTextFace bundled fonts/main-menu.ttf 42"));
        assertTrue(Files.isRegularFile(UiFixtures.externalRoot(configBase()).resolve("images/main-menu/button-disabled.png")));
    }

    @Test
    void overriddenDisabledArtworkCannotMakeADisabledButtonActivate() throws Exception {
        // Disabled buttons get the enabled artwork through an external override.
        byte[] enabledLooking = bundled("images/main-menu/button-normal.png").bytes();
        UiFixtures.resources().put("images/main-menu/button-disabled.png", enabledLooking).writeExternal(configBase());
        UiScreen screen = showMainMenu("en");

        for (String id : DISABLED) {
            double[] p = center(screen, id);
            ui.pointerListener().pointerMoved(p[0], p[1], W, H);
            ui.pointerListener().primaryButton(true, p[0], p[1], W, H);
            ui.pointerListener().primaryButton(false, p[0], p[1], W, H);
            assertEquals(VisualState.DISABLED, screen.state(id));
        }
        assertEquals(List.of(), activations);
    }
}
