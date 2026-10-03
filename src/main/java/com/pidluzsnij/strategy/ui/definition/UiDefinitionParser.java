package com.pidluzsnij.strategy.ui.definition;

import com.pidluzsnij.strategy.text.UnicodeWhiteSpace;
import com.pidluzsnij.strategy.ui.UiException;
import com.pidluzsnij.strategy.ui.resource.ResolvedUiResource;
import com.pidluzsnij.strategy.ui.resource.UiResourcePath;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Parses and validates declarative UI definitions written in JSON. The schema is closed: every object
 * accepts only its documented members, so a definition cannot name classes, methods, scripts, expressions,
 * native libraries or any other executable construct. Behavior identifiers are accepted only when the
 * application declares them as supported; their meaning stays with application code.
 * <p>
 * Image and text components block pointer input to interactive components drawn below them unless they
 * declare {@code "blocking": false}, which marks them as decorations that pointer input passes through.
 * <p>
 * Buttons require an image for every {@linkplain VisualState visual state}, including {@code disabled}, and
 * may declare {@code "enabled": false}; they are enabled by default. A disabled button's behavior identifier
 * must still be supported, but the button can never activate it.
 * <p>
 * A definition is either completely valid, producing a {@link UiDefinition}, or rejected as a whole.
 *
 * <pre>{@code
 * {
 *   "layout": { "width": 1920, "height": 1080 },
 *   "components": [
 *     { "id": "background", "type": "image", "x": 0, "y": 0, "width": 1920, "height": 1080,
 *       "image": "images/background.png" },
 *     { "id": "glow", "type": "image", "x": 740, "y": 480, "width": 440, "height": 140,
 *       "image": "images/glow.png", "blocking": false },
 *     { "id": "title", "type": "text", "x": 560, "y": 100, "width": 800, "height": 120,
 *       "text": { "key": "some.localization.key", "font": "fonts/main.ttf", "size": 64,
 *                 "color": "#FFFFFF", "align": "center" } },
 *     { "id": "start", "type": "button", "x": 760, "y": 500, "width": 400, "height": 100,
 *       "behavior": "some.behavior", "enabled": true,
 *       "states": { "normal": "images/n.png", "hovered": "images/h.png", "pressed": "images/p.png",
 *                   "disabled": "images/d.png" },
 *       "label": { "key": "some.label.key", "font": "fonts/main.ttf", "size": 40, "color": "#202020FF" } }
 *   ]
 * }
 * }</pre>
 */
public final class UiDefinitionParser {

    public static final String STAGE = "load UI definition";

    /** Extension of UI definition resources. */
    public static final String DEFINITION_EXTENSION = ".json";
    /** Extension of image resources (PNG). */
    public static final String IMAGE_EXTENSION = ".png";
    /** Extension of font resources (TrueType). */
    public static final String FONT_EXTENSION = ".ttf";

    public static final int MAX_LOGICAL_SIZE = 16_384;
    public static final int MAX_COORDINATE = 1_000_000;
    public static final int MAX_FONT_SIZE = 1_000;
    public static final int MAX_COMPONENTS = 4_096;

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z][A-Za-z0-9_.-]{0,63}");
    private static final Pattern BEHAVIOR = Pattern.compile("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)*");
    private static final int MAX_BEHAVIOR_LENGTH = 128;
    private static final int MAX_KEY_LENGTH = 256;

    private static final Set<String> TOP_LEVEL = Set.of("layout", "components");
    private static final Set<String> LAYOUT = Set.of("width", "height");
    private static final Set<String> COMMON = Set.of("id", "type", "x", "y", "width", "height");
    private static final Set<String> TEXT_STYLE = Set.of("key", "font", "size", "color", "align");
    private static final Set<String> STATES = Set.of("normal", "hovered", "pressed", "disabled");

    private final Set<String> supportedBehaviors;

    /** @param supportedBehaviors the application-defined semantic behavior identifiers definitions may reference */
    public UiDefinitionParser(Set<String> supportedBehaviors) {
        for (String behavior : supportedBehaviors) {
            if (!isWellFormedBehavior(behavior)) {
                throw new IllegalArgumentException("supported behavior identifiers must be well-formed");
            }
        }
        this.supportedBehaviors = Set.copyOf(supportedBehaviors);
    }

    /** @return whether {@code behavior} is a syntactically valid semantic behavior identifier */
    public static boolean isWellFormedBehavior(String behavior) {
        return behavior != null && behavior.length() <= MAX_BEHAVIOR_LENGTH && BEHAVIOR.matcher(behavior).matches();
    }

    /**
     * @param resource a resolved definition resource
     * @return the complete validated definition
     * @throws UiException when the definition is rejected; nothing of it is retained
     */
    public UiDefinition parse(ResolvedUiResource resource) throws UiException {
        Objects.requireNonNull(resource, "resource");
        if (!resource.path().extension().equals(DEFINITION_EXTENSION)) {
            throw new UiException(STAGE, resource.name(), "UI definitions must be " + DEFINITION_EXTENSION + " resources");
        }
        try {
            String text = decode(resource.buffer());
            return definition(resource.path(), JsonReader.read(text));
        } catch (MalformedDefinitionException e) {
            throw new UiException(STAGE, resource.name(), e.getMessage());
        }
    }

    private static String decode(ByteBuffer bytes) throws MalformedDefinitionException {
        try {
            String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(bytes)
                    .toString();
            return text.startsWith("﻿") ? text.substring(1) : text;
        } catch (CharacterCodingException e) {
            throw new MalformedDefinitionException("the definition is not valid UTF-8");
        }
    }

    private UiDefinition definition(UiResourcePath source, Object root) throws MalformedDefinitionException {
        Map<String, Object> top = object(root, "the definition");
        members(top, "the definition", TOP_LEVEL, TOP_LEVEL);
        Map<String, Object> layout = object(top.get("layout"), "layout");
        members(layout, "layout", LAYOUT, LAYOUT);
        int width = integer(layout, "width", "layout", 1, MAX_LOGICAL_SIZE);
        int height = integer(layout, "height", "layout", 1, MAX_LOGICAL_SIZE);

        if (!(top.get("components") instanceof List<?> list)) {
            throw new MalformedDefinitionException("'components' must be an array");
        }
        if (list.size() > MAX_COMPONENTS) {
            throw new MalformedDefinitionException("a definition may contain at most " + MAX_COMPONENTS + " components");
        }
        List<UiComponent> components = new ArrayList<>(list.size());
        Set<String> ids = new HashSet<>();
        for (int index = 0; index < list.size(); index++) {
            String context = "component #" + (index + 1);
            UiComponent component = component(list.get(index), context);
            if (!ids.add(component.id())) {
                throw new MalformedDefinitionException(context + " repeats the component identifier '" + component.id() + "'");
            }
            components.add(component);
        }
        return new UiDefinition(source, width, height, components);
    }

    private UiComponent component(Object value, String context) throws MalformedDefinitionException {
        Map<String, Object> map = object(value, context);
        String id = string(map, "id", context);
        if (!IDENTIFIER.matcher(id).matches()) {
            throw new MalformedDefinitionException(context + " has a malformed 'id'");
        }
        context = context + " ('" + id + "')";
        String type = string(map, "type", context);
        Bounds bounds;
        switch (type) {
            case "image" -> {
                members(map, context, union(COMMON, Set.of("image", "blocking")), union(COMMON, Set.of("image")));
                bounds = bounds(map, context);
                return new UiComponent.Image(id, bounds, resource(map, "image", context, IMAGE_EXTENSION),
                        blocking(map, context));
            }
            case "text" -> {
                members(map, context, union(COMMON, Set.of("text", "blocking")), union(COMMON, Set.of("text")));
                bounds = bounds(map, context);
                return new UiComponent.Text(id, bounds, textStyle(map.get("text"), context + " text"),
                        blocking(map, context));
            }
            case "button" -> {
                Set<String> required = union(COMMON, Set.of("behavior", "states"));
                members(map, context, union(required, Set.of("label", "enabled")), required);
                bounds = bounds(map, context);
                String behavior = string(map, "behavior", context);
                if (!isWellFormedBehavior(behavior)) {
                    throw new MalformedDefinitionException(context + " has a malformed 'behavior' identifier");
                }
                if (!supportedBehaviors.contains(behavior)) {
                    throw new MalformedDefinitionException(context + " references the unsupported behavior '"
                            + behavior + "'");
                }
                Map<String, Object> statesMap = object(map.get("states"), context + " states");
                members(statesMap, context + " states", STATES, STATES);
                Map<VisualState, UiResourcePath> states = new EnumMap<>(VisualState.class);
                for (VisualState state : VisualState.values()) {
                    states.put(state, resource(statesMap, state.id(), context + " states", IMAGE_EXTENSION));
                }
                Optional<TextStyle> label = map.containsKey("label")
                        ? Optional.of(textStyle(map.get("label"), context + " label"))
                        : Optional.empty();
                return new UiComponent.Button(id, bounds, behavior, enabled(map, context), states, label);
            }
            default -> throw new MalformedDefinitionException(context + " has the unsupported type '"
                    + (IDENTIFIER.matcher(type).matches() ? type : "?") + "'");
        }
    }

    /** @return the optional {@code enabled} member of a button; buttons are enabled by default */
    private static boolean enabled(Map<String, Object> map, String context) throws MalformedDefinitionException {
        if (!map.containsKey("enabled")) {
            return true;
        }
        if (!(map.get("enabled") instanceof Boolean value)) {
            throw new MalformedDefinitionException(context + " member 'enabled' must be true or false");
        }
        return value;
    }

    /** @return the optional {@code blocking} member; components block pointer input by default */
    private static boolean blocking(Map<String, Object> map, String context) throws MalformedDefinitionException {
        if (!map.containsKey("blocking")) {
            return true;
        }
        if (!(map.get("blocking") instanceof Boolean value)) {
            throw new MalformedDefinitionException(context + " member 'blocking' must be true or false");
        }
        return value;
    }

    private static Bounds bounds(Map<String, Object> map, String context) throws MalformedDefinitionException {
        return new Bounds(
                integer(map, "x", context, -MAX_COORDINATE, MAX_COORDINATE),
                integer(map, "y", context, -MAX_COORDINATE, MAX_COORDINATE),
                integer(map, "width", context, 1, MAX_COORDINATE),
                integer(map, "height", context, 1, MAX_COORDINATE));
    }

    private static TextStyle textStyle(Object value, String context) throws MalformedDefinitionException {
        Map<String, Object> map = object(value, context);
        members(map, context, TEXT_STYLE, Set.of("key", "font", "size", "color"));
        String key = string(map, "key", context);
        if (!isUsableLocalizationKey(key)) {
            throw new MalformedDefinitionException(context + " has a malformed localization 'key'");
        }
        UiResourcePath font = resource(map, "font", context, FONT_EXTENSION);
        int size = integer(map, "size", context, 1, MAX_FONT_SIZE);
        String color = string(map, "color", context);
        if (!UiColor.isValid(color)) {
            throw new MalformedDefinitionException(context + " has a 'color' that is not #RRGGBB or #RRGGBBAA");
        }
        TextAlign align = TextAlign.LEFT;
        if (map.containsKey("align")) {
            align = switch (string(map, "align", context)) {
                case "left" -> TextAlign.LEFT;
                case "center" -> TextAlign.CENTER;
                case "right" -> TextAlign.RIGHT;
                default -> throw new MalformedDefinitionException(context + " has an unsupported 'align' value");
            };
        }
        return new TextStyle(key, font, size, UiColor.parse(color), align);
    }

    /** Keys are matched exactly by localization, which trims keys and forbids '=' in them. */
    private static boolean isUsableLocalizationKey(String key) {
        if (key.isEmpty() || key.length() > MAX_KEY_LENGTH || UnicodeWhiteSpace.isBlank(key)
                || UnicodeWhiteSpace.hasSurroundingWhiteSpace(key) || key.indexOf('=') >= 0) {
            return false;
        }
        return key.codePoints().noneMatch(c -> Character.isISOControl(c) || c == ' ' || c == ' ');
    }

    private static UiResourcePath resource(Map<String, Object> map, String member, String context, String extension)
            throws MalformedDefinitionException {
        String value = string(map, member, context);
        if (!UiResourcePath.isValid(value)) {
            throw new MalformedDefinitionException(context + " has an invalid resource reference in '" + member
                    + "': only relative paths confined to the UI resource root are allowed");
        }
        UiResourcePath path = UiResourcePath.of(value);
        if (!path.extension().equals(extension)) {
            throw new MalformedDefinitionException(context + " references an unsupported resource type in '"
                    + member + "' (expected " + extension + ")");
        }
        return path;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value, String context) throws MalformedDefinitionException {
        if (!(value instanceof Map<?, ?> map)) {
            throw new MalformedDefinitionException(context + " must be an object");
        }
        return (Map<String, Object>) map;
    }

    private static void members(Map<String, Object> map, String context, Set<String> allowed, Set<String> required)
            throws MalformedDefinitionException {
        for (String key : map.keySet()) {
            if (!allowed.contains(key)) {
                throw new MalformedDefinitionException(context + " contains the unsupported member "
                        + (IDENTIFIER.matcher(key).matches() ? "'" + key + "'" : "(name omitted)"));
            }
        }
        for (String key : required) {
            if (!map.containsKey(key)) {
                throw new MalformedDefinitionException(context + " lacks the required member '" + key + "'");
            }
        }
    }

    private static String string(Map<String, Object> map, String member, String context)
            throws MalformedDefinitionException {
        if (!(map.get(member) instanceof String value)) {
            throw new MalformedDefinitionException(context + " member '" + member + "' must be a string");
        }
        return value;
    }

    private static int integer(Map<String, Object> map, String member, String context, int min, int max)
            throws MalformedDefinitionException {
        if (!(map.get(member) instanceof BigDecimal number)) {
            throw new MalformedDefinitionException(context + " member '" + member + "' must be a number");
        }
        int value;
        try {
            value = number.intValueExact();
        } catch (ArithmeticException e) {
            throw new MalformedDefinitionException(context + " member '" + member + "' must be an integer within "
                    + min + ".." + max);
        }
        if (value < min || value > max) {
            throw new MalformedDefinitionException(context + " member '" + member + "' must be within "
                    + min + ".." + max);
        }
        return value;
    }

    private static Set<String> union(Set<String> a, Set<String> b) {
        Set<String> result = new HashSet<>(a);
        result.addAll(b);
        return result;
    }
}
