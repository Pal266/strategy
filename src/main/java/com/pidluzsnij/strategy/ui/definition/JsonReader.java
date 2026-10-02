package com.pidluzsnij.strategy.ui.definition;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Strict reader for the JSON (RFC 8259) syntax of UI definitions. Objects become insertion-ordered maps,
 * arrays lists, strings strings, numbers {@link BigDecimal}s and literals {@link Boolean}s or
 * {@link #NULL}. Duplicate object keys, trailing content, excessive nesting and unpaired surrogates are
 * rejected. Error messages carry positions only, never content.
 */
final class JsonReader {

    /** Marker for the JSON {@code null} literal. */
    static final Object NULL = new Object() {
        @Override
        public String toString() {
            return "null";
        }
    };

    static final int MAX_DEPTH = 32;

    private final String text;
    private int position;
    private int depth;

    private JsonReader(String text) {
        this.text = text;
    }

    /** @throws MalformedDefinitionException when {@code text} is not exactly one valid JSON value */
    static Object read(String text) throws MalformedDefinitionException {
        JsonReader reader = new JsonReader(text);
        reader.skipWhitespace();
        Object value = reader.value();
        reader.skipWhitespace();
        if (reader.position != text.length()) {
            throw reader.error("unexpected content after the top-level value");
        }
        return value;
    }

    private Object value() throws MalformedDefinitionException {
        if (position >= text.length()) {
            throw error("unexpected end of input");
        }
        char c = text.charAt(position);
        return switch (c) {
            case '{' -> object();
            case '[' -> array();
            case '"' -> string();
            case 't' -> literal("true", Boolean.TRUE);
            case 'f' -> literal("false", Boolean.FALSE);
            case 'n' -> literal("null", NULL);
            default -> {
                if (c == '-' || (c >= '0' && c <= '9')) {
                    yield number();
                }
                throw error("unexpected character");
            }
        };
    }

    private Map<String, Object> object() throws MalformedDefinitionException {
        enter();
        position++;
        Map<String, Object> members = new LinkedHashMap<>();
        skipWhitespace();
        if (peek() == '}') {
            position++;
            depth--;
            return Collections.unmodifiableMap(members);
        }
        while (true) {
            skipWhitespace();
            if (peek() != '"') {
                throw error("expected an object key");
            }
            int keyPosition = position;
            String key = string();
            skipWhitespace();
            expect(':');
            skipWhitespace();
            Object value = value();
            if (members.containsKey(key)) {
                position = keyPosition;
                throw error("duplicate object key");
            }
            members.put(key, value);
            skipWhitespace();
            char c = peek();
            position++;
            if (c == '}') {
                depth--;
                return Collections.unmodifiableMap(members);
            }
            if (c != ',') {
                position--;
                throw error("expected ',' or '}'");
            }
        }
    }

    private List<Object> array() throws MalformedDefinitionException {
        enter();
        position++;
        List<Object> elements = new ArrayList<>();
        skipWhitespace();
        if (peek() == ']') {
            position++;
            depth--;
            return Collections.unmodifiableList(elements);
        }
        while (true) {
            skipWhitespace();
            elements.add(value());
            skipWhitespace();
            char c = peek();
            position++;
            if (c == ']') {
                depth--;
                return Collections.unmodifiableList(elements);
            }
            if (c != ',') {
                position--;
                throw error("expected ',' or ']'");
            }
        }
    }

    private String string() throws MalformedDefinitionException {
        position++;
        StringBuilder result = new StringBuilder();
        while (true) {
            if (position >= text.length()) {
                throw error("unterminated string");
            }
            char c = text.charAt(position++);
            if (c == '"') {
                break;
            }
            if (c < 0x20) {
                position--;
                throw error("unescaped control character in string");
            }
            if (c != '\\') {
                result.append(c);
                continue;
            }
            if (position >= text.length()) {
                throw error("unterminated escape sequence");
            }
            char escape = text.charAt(position++);
            switch (escape) {
                case '"' -> result.append('"');
                case '\\' -> result.append('\\');
                case '/' -> result.append('/');
                case 'b' -> result.append('\b');
                case 'f' -> result.append('\f');
                case 'n' -> result.append('\n');
                case 'r' -> result.append('\r');
                case 't' -> result.append('\t');
                case 'u' -> result.append(unicodeEscape());
                default -> {
                    position--;
                    throw error("invalid escape sequence");
                }
            }
        }
        String value = result.toString();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (i + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(i + 1))) {
                    throw error("unpaired surrogate in string");
                }
                i++;
            } else if (Character.isLowSurrogate(c)) {
                throw error("unpaired surrogate in string");
            }
        }
        return value;
    }

    private char unicodeEscape() throws MalformedDefinitionException {
        if (position + 4 > text.length()) {
            throw error("incomplete unicode escape");
        }
        int value = 0;
        for (int i = 0; i < 4; i++) {
            int digit = Character.digit(text.charAt(position + i), 16);
            if (digit < 0) {
                throw error("invalid unicode escape");
            }
            value = value * 16 + digit;
        }
        position += 4;
        return (char) value;
    }

    private BigDecimal number() throws MalformedDefinitionException {
        int start = position;
        if (peek() == '-') {
            position++;
        }
        if (peek() == '0') {
            position++;
        } else if (isDigit(peek()) && peek() != '0') {
            digits();
        } else {
            throw error("invalid number");
        }
        if (peek() == '.') {
            position++;
            if (!isDigit(peek())) {
                throw error("invalid number");
            }
            digits();
        }
        if (peek() == 'e' || peek() == 'E') {
            position++;
            if (peek() == '+' || peek() == '-') {
                position++;
            }
            if (!isDigit(peek())) {
                throw error("invalid number");
            }
            digits();
        }
        if (position - start > 64) {
            throw error("number is too long");
        }
        try {
            return new BigDecimal(text.substring(start, position));
        } catch (NumberFormatException | ArithmeticException e) {
            throw error("invalid number");
        }
    }

    private void digits() {
        while (isDigit(peek())) {
            position++;
        }
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private Object literal(String literal, Object value) throws MalformedDefinitionException {
        if (!text.startsWith(literal, position)) {
            throw error("invalid literal");
        }
        position += literal.length();
        return value;
    }

    private void enter() throws MalformedDefinitionException {
        if (++depth > MAX_DEPTH) {
            throw error("nesting is deeper than " + MAX_DEPTH + " levels");
        }
    }

    private void expect(char expected) throws MalformedDefinitionException {
        if (peek() != expected) {
            throw error("expected '" + expected + "'");
        }
        position++;
    }

    private char peek() {
        return position < text.length() ? text.charAt(position) : '\0';
    }

    private void skipWhitespace() {
        while (position < text.length()) {
            char c = text.charAt(position);
            if (c != ' ' && c != '\t' && c != '\n' && c != '\r') {
                return;
            }
            position++;
        }
    }

    private MalformedDefinitionException error(String problem) {
        int line = 1;
        int column = 1;
        for (int i = 0; i < Math.min(position, text.length()); i++) {
            if (text.charAt(i) == '\n') {
                line++;
                column = 1;
            } else {
                column++;
            }
        }
        return new MalformedDefinitionException("malformed JSON at line " + line + ", column " + column + ": " + problem);
    }
}
