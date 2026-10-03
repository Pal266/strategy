package com.pidluzsnij.strategy.ui.definition;

import com.pidluzsnij.strategy.ui.UiException;
import com.pidluzsnij.strategy.ui.resource.ResolvedUiResource;
import com.pidluzsnij.strategy.ui.resource.UiResourceOrigin;
import com.pidluzsnij.strategy.ui.resource.UiResourcePath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
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

/** Declarative UI definitions: schema, validation, all-or-nothing rejection and absence of executable content. */
class UiDefinitionParserTest {

    private static final Set<String> BEHAVIORS = Set.of("test.start", "test.quit");
    private final UiDefinitionParser parser = new UiDefinitionParser(BEHAVIORS);

    private static ResolvedUiResource resource(String json) {
        return resource("screens/test.json", json);
    }

    private static ResolvedUiResource resource(String path, String json) {
        return new ResolvedUiResource(UiResourcePath.of(path), UiResourceOrigin.EXTERNAL,
                json.getBytes(StandardCharsets.UTF_8));
    }

    private UiDefinition parse(String json) throws UiException {
        return parser.parse(resource(json));
    }

    private UiException rejected(String json) {
        UiException failure = assertThrows(UiException.class, () -> parse(json));
        assertEquals(UiDefinitionParser.STAGE, failure.stage());
        assertEquals("external screens/test.json", failure.resource());
        return failure;
    }

    private static final String VALID_IMAGE = image("background", 0, 0, 100, 50, "images/bg.png");

    // --- Declarative presentation and layout ----------------------------------------------

    @Test
    void twoDefinitionsWithTheSameIdentifiersCarryTheirOwnPresentation() throws Exception {
        String first = definition(1920, 1080,
                image("background", 0, 0, 1920, 1080, "images/bg-a.png"),
                text("title", 100, 50, 800, 100, style("test.title", "fonts/a.ttf", 48, "#FFFFFF", "left")),
                button("start", 760, 500, 400, 100, "test.start", "images/n-a.png", "images/h-a.png",
                        "images/p-a.png", style("test.start.label", "fonts/a.ttf", 32, "#000000", "center")));
        String second = definition(1280, 1024,
                text("title", 10, 900, 1200, 80, style("test.title", "fonts/b.ttf", 30, "#FF000080", "right")),
                button("start", 20, 20, 300, 60, "test.start", "images/n-b.png", "images/h-b.png",
                        "images/p-b.png", style("test.start.label", "fonts/b.ttf", 20, "#00FF00", null)),
                image("background", 5, 6, 700, 800, "images/bg-b.png"));

        UiDefinition a = parse(first);
        UiDefinition b = parse(second);

        assertEquals(1920, a.logicalWidth());
        assertEquals(1080, a.logicalHeight());
        assertEquals(1280, b.logicalWidth());
        assertEquals(1024, b.logicalHeight());
        assertEquals(List.of("background", "title", "start"), a.components().stream().map(UiComponent::id).toList());
        assertEquals(List.of("title", "start", "background"), b.components().stream().map(UiComponent::id).toList());

        assertEquals(new UiComponent.Image("background", new Bounds(0, 0, 1920, 1080), UiResourcePath.of("images/bg-a.png"), true),
                a.component("background"));
        assertEquals(new UiComponent.Image("background", new Bounds(5, 6, 700, 800), UiResourcePath.of("images/bg-b.png"), true),
                b.component("background"));
        assertEquals(new TextStyle("test.title", UiResourcePath.of("fonts/a.ttf"), 48, new UiColor(255, 255, 255, 255),
                TextAlign.LEFT), ((UiComponent.Text) a.component("title")).text());
        assertEquals(new TextStyle("test.title", UiResourcePath.of("fonts/b.ttf"), 30, new UiColor(255, 0, 0, 128),
                TextAlign.RIGHT), ((UiComponent.Text) b.component("title")).text());

        UiComponent.Button startA = (UiComponent.Button) a.component("start");
        UiComponent.Button startB = (UiComponent.Button) b.component("start");
        assertEquals("test.start", startA.behavior());
        assertEquals("test.start", startB.behavior());
        assertEquals(new Bounds(760, 500, 400, 100), startA.bounds());
        assertEquals(new Bounds(20, 20, 300, 60), startB.bounds());
        assertEquals(Map.of(VisualState.NORMAL, UiResourcePath.of("images/n-a.png"),
                VisualState.HOVERED, UiResourcePath.of("images/h-a.png"),
                VisualState.PRESSED, UiResourcePath.of("images/p-a.png"),
                VisualState.DISABLED, UiResourcePath.of("images/n-a.png")), startA.states());
        assertEquals(UiResourcePath.of("images/p-b.png"), startB.image(VisualState.PRESSED));
        assertEquals(TextAlign.LEFT, startB.label().orElseThrow().align(), "alignment defaults to left");
        assertEquals(20, startB.label().orElseThrow().size());
        assertEquals("fonts/b.ttf", startB.label().orElseThrow().font().value());
    }

    @Test
    void decorationsCanBeMarkedAsNotBlocking() throws Exception {
        UiDefinition parsed = parse(definition(100, 100,
                image("solid", 0, 0, 10, 10, "images/a.png"),
                "{\"id\": \"glow\", \"type\": \"image\", \"x\": 0, \"y\": 0, \"width\": 10, \"height\": 10, "
                        + "\"image\": \"images/glow.png\", \"blocking\": false}",
                "{\"id\": \"caption\", \"type\": \"text\", \"x\": 0, \"y\": 0, \"width\": 10, \"height\": 10, "
                        + "\"blocking\": false, \"text\": " + style("k", "fonts/a.ttf", 10, "#FFFFFF", null) + "}",
                "{\"id\": \"explicit\", \"type\": \"image\", \"x\": 0, \"y\": 0, \"width\": 10, \"height\": 10, "
                        + "\"image\": \"images/a.png\", \"blocking\": true}",
                button("start", 0, 0, 10, 10, "test.start", "a.png", "b.png", "c.png", null)));

        assertTrue(parsed.component("solid").blocking(), "components block by default");
        assertFalse(parsed.component("glow").blocking());
        assertFalse(parsed.component("caption").blocking());
        assertTrue(parsed.component("explicit").blocking());
        assertTrue(parsed.component("start").blocking(), "interactive components always block");
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"false\"", "0", "null", "[]"})
    void blockingMustBeABoolean(String value) {
        assertTrue(rejected(definition(100, 100, "{\"id\": \"a\", \"type\": \"image\", \"x\": 0, \"y\": 0, "
                + "\"width\": 10, \"height\": 10, \"image\": \"a.png\", \"blocking\": " + value + "}"))
                .reason().contains("'blocking'"));
    }

    @Test
    void buttonsCannotBeMarkedAsNotBlocking() {
        String component = "{\"id\": \"start\", \"type\": \"button\", \"x\": 0, \"y\": 0, \"width\": 10, "
                + "\"height\": 10, \"behavior\": \"test.start\", \"states\": {\"normal\": \"a.png\", "
                + "\"hovered\": \"b.png\", \"pressed\": \"c.png\"}, \"blocking\": false}";
        assertTrue(rejected(definition(100, 100, component)).reason().contains("unsupported member 'blocking'"));
    }

    @Test
    void textStylesReferenceLocalizationKeysNotText() throws Exception {
        UiDefinition parsed = parse(definition(100, 100,
                text("greeting", 0, 0, 100, 20, style("test.greeting", "fonts/a.ttf", 12, "#FFFFFF", null))));

        assertEquals("test.greeting", parsed.textStyles().get(0).localizationKey());
    }

    @Test
    void definitionsMustBeJsonResources() {
        UiException failure = assertThrows(UiException.class,
                () -> parser.parse(resource("screens/test.txt", definition(10, 10, VALID_IMAGE))));
        assertTrue(failure.reason().contains(".json"));
    }

    @Test
    void emptyComponentListIsValid() throws Exception {
        assertEquals(List.of(), parse(definition(10, 10)).components());
    }

    // --- No executable behavior ------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"script", "class", "method", "expression", "onClick", "action", "nativeLibrary",
            "handler", "code"})
    void executableMembersAreUnsupported(String member) {
        String component = "{\"id\": \"start\", \"type\": \"button\", \"x\": 0, \"y\": 0, \"width\": 10, "
                + "\"height\": 10, \"behavior\": \"test.start\", \"states\": {\"normal\": \"a.png\", "
                + "\"hovered\": \"b.png\", \"pressed\": \"c.png\"}, \"" + member + "\": \"java.lang.System.exit(0)\"}";
        UiException failure = rejected(definition(100, 100, component));
        assertTrue(failure.reason().contains("unsupported member"), failure.reason());
        assertFalse(failure.getMessage().contains("System.exit"), "content is never quoted");
    }

    @ParameterizedTest
    @ValueSource(strings = {"script", "class", "java", "native", "eval"})
    void executableComponentTypesAreUnsupported(String type) {
        String component = "{\"id\": \"x\", \"type\": \"" + type + "\", \"x\": 0, \"y\": 0, \"width\": 1, \"height\": 1}";
        assertTrue(rejected(definition(10, 10, component)).reason().contains("unsupported type"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"java.lang.System.exit", "com.example.Foo#bar", "${java.home}", "Runtime.exec(\\\"x\\\")",
            "test.start()", "javascript:alert(1)", "test..start", ".test", "test.", "", "1test"})
    void malformedBehaviorIdentifiersAreRejected(String behavior) {
        String component = button("start", 0, 0, 10, 10, behavior, "a.png", "b.png", "c.png", null);
        assertTrue(rejected(definition(100, 100, component)).reason().contains("malformed 'behavior'"));
    }

    @Test
    void parserHasNoReflectiveOrScriptingEntryPoint() {
        for (Method method : UiDefinitionParser.class.getDeclaredMethods()) {
            if (Modifier.isPublic(method.getModifiers())) {
                for (Class<?> parameter : method.getParameterTypes()) {
                    assertFalse(parameter == Class.class || parameter == Method.class
                            || parameter.getName().startsWith("javax.script"), method.toString());
                }
            }
        }
    }

    // --- Semantic behavior identifiers -----------------------------------------------------

    @Test
    void supportedBehaviorIsRepresentedAsSemanticData() throws Exception {
        UiDefinition parsed = parse(definition(100, 100,
                button("start", 0, 0, 10, 10, "test.quit", "a.png", "b.png", "c.png", null)));

        UiComponent.Button button = assertInstanceOf(UiComponent.Button.class, parsed.component("start"));
        assertEquals("test.quit", button.behavior());
    }

    @ParameterizedTest
    @ValueSource(strings = {"test.unknown", "other.start", "test.start.extra", "test"})
    void unknownBehaviorsAreRejected(String behavior) {
        UiException failure = rejected(definition(100, 100,
                button("start", 0, 0, 10, 10, behavior, "a.png", "b.png", "c.png", null)));
        assertTrue(failure.reason().contains("unsupported behavior"), failure.reason());
    }

    @Test
    void anApplicationWithoutBehaviorsAcceptsNoButtons() {
        UiDefinitionParser none = new UiDefinitionParser(Set.of());
        assertThrows(UiException.class, () -> none.parse(resource(definition(100, 100,
                button("start", 0, 0, 10, 10, "test.start", "a.png", "b.png", "c.png", null)))));
    }

    @Test
    void supportedBehaviorsMustBeWellFormed() {
        assertThrows(IllegalArgumentException.class, () -> new UiDefinitionParser(Set.of("Bad Behavior")));
    }

    // --- All-or-nothing validation ---------------------------------------------------------

    @Test
    void malformedSyntaxAfterAValidComponentRejectsTheDefinition() {
        assertTrue(rejected(definition(100, 100, VALID_IMAGE, "{\"id\": \"broken\",")).reason().contains("malformed JSON"));
    }

    @Test
    void unsupportedRequiredValueAfterAValidComponentRejectsTheDefinition() {
        assertTrue(rejected(definition(100, 100, VALID_IMAGE,
                text("t", 0, 0, 10, 10, style("k", "fonts/a.ttf", 12, "#FFFFFF", "justify")))).reason().contains("align"));
    }

    @Test
    void invalidResourceReferenceAfterAValidComponentRejectsTheDefinition() {
        assertTrue(rejected(definition(100, 100, VALID_IMAGE, image("second", 0, 0, 10, 10, "../secret.png")))
                .reason().contains("invalid resource reference"));
    }

    @Test
    void duplicateIdentifierAfterAValidComponentRejectsTheDefinition() {
        assertTrue(rejected(definition(100, 100, VALID_IMAGE, image("background", 0, 0, 10, 10, "images/x.png")))
                .reason().contains("repeats the component identifier"));
    }

    @Test
    void unsupportedBehaviorAfterAValidComponentRejectsTheDefinition() {
        assertTrue(rejected(definition(100, 100, VALID_IMAGE,
                button("b", 0, 0, 10, 10, "test.missing", "a.png", "b.png", "c.png", null)))
                .reason().contains("unsupported behavior"));
    }

    // --- Property validation ---------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"layout\": {\"width\": 0, \"height\": 10}, \"components\": []}",
            "{\"layout\": {\"width\": 10.5, \"height\": 10}, \"components\": []}",
            "{\"layout\": {\"width\": \"10\", \"height\": 10}, \"components\": []}",
            "{\"layout\": {\"width\": 10, \"height\": 99999}, \"components\": []}",
            "{\"layout\": {\"width\": 10}, \"components\": []}",
            "{\"layout\": {\"width\": 10, \"height\": 10, \"depth\": 3}, \"components\": []}",
            "{\"layout\": {\"width\": 10, \"height\": 10}}",
            "{\"layout\": {\"width\": 10, \"height\": 10}, \"components\": {}}",
            "{\"layout\": {\"width\": 10, \"height\": 10}, \"components\": [], \"extra\": 1}",
            "[]",
            "{\"layout\": {\"width\": 10, \"height\": 10}, \"layout\": {\"width\": 10, \"height\": 10}, \"components\": []}",
            "{\"layout\": {\"width\": 10, \"height\": 10}, \"components\": []} trailing",
    })
    void invalidDocumentsAreRejected(String json) {
        rejected(json);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"id\": \"a\", \"type\": \"image\", \"x\": 0, \"y\": 0, \"width\": 0, \"height\": 10, \"image\": \"a.png\"}",
            "{\"id\": \"a\", \"type\": \"image\", \"x\": 0, \"y\": 0, \"width\": 10, \"height\": -1, \"image\": \"a.png\"}",
            "{\"id\": \"a\", \"type\": \"image\", \"x\": 0, \"y\": 0, \"width\": 10, \"height\": 10}",
            "{\"id\": \"a\", \"type\": \"image\", \"x\": 0, \"y\": 0, \"width\": 10, \"height\": 10, \"image\": \"a.jpg\"}",
            "{\"id\": \"a\", \"type\": \"image\", \"x\": 0, \"y\": 0, \"width\": 10, \"height\": 10, \"image\": \"/etc/a.png\"}",
            "{\"id\": \"a b\", \"type\": \"image\", \"x\": 0, \"y\": 0, \"width\": 10, \"height\": 10, \"image\": \"a.png\"}",
            "{\"id\": \"a\", \"type\": \"image\", \"x\": 1.5, \"y\": 0, \"width\": 10, \"height\": 10, \"image\": \"a.png\"}",
            "{\"id\": \"a\", \"type\": \"text\", \"x\": 0, \"y\": 0, \"width\": 10, \"height\": 10, \"text\": "
                    + "{\"key\": \"k\", \"font\": \"f.otf\", \"size\": 10, \"color\": \"#FFFFFF\"}}",
            "{\"id\": \"a\", \"type\": \"text\", \"x\": 0, \"y\": 0, \"width\": 10, \"height\": 10, \"text\": "
                    + "{\"key\": \"k\", \"font\": \"f.ttf\", \"size\": 0, \"color\": \"#FFFFFF\"}}",
            "{\"id\": \"a\", \"type\": \"text\", \"x\": 0, \"y\": 0, \"width\": 10, \"height\": 10, \"text\": "
                    + "{\"key\": \"k\", \"font\": \"f.ttf\", \"size\": 10, \"color\": \"white\"}}",
            "{\"id\": \"a\", \"type\": \"text\", \"x\": 0, \"y\": 0, \"width\": 10, \"height\": 10, \"text\": "
                    + "{\"key\": \" \", \"font\": \"f.ttf\", \"size\": 10, \"color\": \"#FFFFFF\"}}",
            "{\"id\": \"a\", \"type\": \"text\", \"x\": 0, \"y\": 0, \"width\": 10, \"height\": 10, \"text\": "
                    + "{\"key\": \"k=v\", \"font\": \"f.ttf\", \"size\": 10, \"color\": \"#FFFFFF\"}}",
            "{\"id\": \"a\", \"type\": \"text\", \"x\": 0, \"y\": 0, \"width\": 10, \"height\": 10, \"text\": "
                    + "{\"font\": \"f.ttf\", \"size\": 10, \"color\": \"#FFFFFF\"}}",
            "{\"id\": \"a\", \"type\": \"text\", \"x\": 0, \"y\": 0, \"width\": 10, \"height\": 10, \"text\": \"Hello\"}",
            "{\"id\": \"a\", \"type\": \"button\", \"x\": 0, \"y\": 0, \"width\": 10, \"height\": 10, "
                    + "\"behavior\": \"test.start\", \"states\": {\"normal\": \"a.png\", \"hovered\": \"b.png\"}}",
            "{\"id\": \"a\", \"type\": \"button\", \"x\": 0, \"y\": 0, \"width\": 10, \"height\": 10, "
                    + "\"behavior\": \"test.start\", \"states\": {\"normal\": \"a.png\", \"hovered\": \"b.png\", "
                    + "\"pressed\": \"c.png\", \"disabled\": \"d.png\", \"focused\": \"e.png\"}}",
    })
    void invalidComponentsAreRejected(String component) {
        rejected(definition(100, 100, component));
    }

    @Test
    void invalidUtf8IsRejected() {
        byte[] bytes = {'{', '"', (byte) 0xC3, (byte) 0x28, '"', ':', '1', '}'};
        UiException failure = assertThrows(UiException.class, () -> parser.parse(
                new ResolvedUiResource(UiResourcePath.of("a.json"), UiResourceOrigin.BUNDLED, bytes)));
        assertTrue(failure.reason().contains("UTF-8"));
    }

    @Test
    void utf8ByteOrderMarkIsAccepted() throws Exception {
        assertEquals(10, parse("\uFEFF" + definition(10, 20)).logicalWidth());
    }

    @Test
    void unicodeLocalizationKeysAndEscapesAreAccepted() throws Exception {
        UiDefinition parsed = parse(definition(100, 100,
                text("t", 0, 0, 10, 10, style("test.\\u0457\\u0436\\u0430\\u043a", "fonts/a.ttf", 12, "#FFFFFF", null))));
        assertEquals("test.їжак", parsed.textStyles().get(0).localizationKey());
    }

    // --- SPEC007: enabled/disabled buttons ----------------------------------------------------

    private static String fourStates(String enabledMember, String states) {
        return "{\"id\": \"b\", \"type\": \"button\", \"x\": 0, \"y\": 0, \"width\": 10, \"height\": 10, "
                + "\"behavior\": \"test.start\"" + enabledMember + ", \"states\": " + states + "}";
    }

    private static final String ALL_STATES = "{\"normal\": \"images/n.png\", \"hovered\": \"images/h.png\", "
            + "\"pressed\": \"images/p.png\", \"disabled\": \"images/d.png\"}";

    @Test
    void disabledButtonWithAllFourStatesIsAccepted() throws Exception {
        UiDefinition parsed = parse(definition(100, 100, fourStates(", \"enabled\": false", ALL_STATES)));

        UiComponent.Button button = (UiComponent.Button) parsed.component("b");
        assertFalse(button.enabled());
        assertEquals("test.start", button.behavior(), "the behavior stays declared data");
        assertEquals(UiResourcePath.of("images/d.png"), button.image(VisualState.DISABLED));
        assertTrue(parsed.imageResources().contains(UiResourcePath.of("images/d.png")),
                "the disabled image is loaded for rendering");
        assertTrue(button.blocking());
    }

    @Test
    void buttonsAreEnabledExplicitlyOrByDefault() throws Exception {
        assertTrue(((UiComponent.Button) parse(definition(100, 100, fourStates(", \"enabled\": true", ALL_STATES)))
                .component("b")).enabled());
        assertTrue(((UiComponent.Button) parse(definition(100, 100, fourStates("", ALL_STATES)))
                .component("b")).enabled());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"normal\": \"images/n.png\", \"hovered\": \"images/h.png\", \"pressed\": \"images/p.png\"}",
            "{\"normal\": \"images/n.png\", \"hovered\": \"images/h.png\", \"pressed\": \"images/p.png\", "
                    + "\"disabled\": \"images/d.jpg\"}",
            "{\"normal\": \"images/n.png\", \"hovered\": \"images/h.png\", \"pressed\": \"images/p.png\", "
                    + "\"disabled\": \"../d.png\"}",
            "{\"normal\": \"images/n.png\", \"hovered\": \"images/h.png\", \"pressed\": \"images/p.png\", "
                    + "\"disabled\": 1}",
    })
    void buttonWithoutAUsableDisabledStateIsRejectedAsAWhole(String states) {
        for (String enabled : List.of("", ", \"enabled\": true", ", \"enabled\": false")) {
            UiException failure = rejected(definition(100, 100,
                    image("valid", 0, 0, 10, 10, "images/bg.png"), fourStates(enabled, states)));
            assertTrue(failure.reason().contains("disabled"), failure.reason());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"false\"", "0", "null", "{}"})
    void malformedEnabledValuesAreRejected(String value) {
        UiException failure = rejected(definition(100, 100, fourStates(", \"enabled\": " + value, ALL_STATES)));
        assertTrue(failure.reason().contains("enabled"), failure.reason());
    }

    @Test
    void disabledButtonsMustStillReferenceASupportedBehavior() {
        rejected(definition(100, 100, fourStates(", \"enabled\": false", ALL_STATES).replace("test.start", "test.unknown")));
        rejected(definition(100, 100, fourStates(", \"enabled\": false", ALL_STATES).replace("test.start", "Bad Id")));
    }

    @Test
    void enabledIsNotAcceptedOnNonInteractiveComponents() {
        rejected(definition(100, 100, image("i", 0, 0, 10, 10, "images/bg.png").replace("}", ", \"enabled\": false}")));
    }
}
