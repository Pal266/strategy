package com.pidluzsnij.strategy.ui.definition;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Strict JSON syntax of UI definitions. */
class JsonReaderTest {

    @Test
    void readsAllValueKindsInOrder() throws Exception {
        Object value = JsonReader.read(" {\"b\": [1, -2.5e1, true, false, null, \"x\\n\\u00e9\\ud83d\\ude00\"], \"a\": {}} ");
        Map<?, ?> map = (Map<?, ?>) value;
        assertEquals(List.of("b", "a"), List.copyOf(map.keySet()));
        List<?> list = (List<?>) map.get("b");
        assertEquals(new BigDecimal("1"), list.get(0));
        assertEquals(new BigDecimal("-2.5e1"), list.get(1));
        assertEquals(Boolean.TRUE, list.get(2));
        assertEquals(Boolean.FALSE, list.get(3));
        assertSame(JsonReader.NULL, list.get(4));
        assertEquals("x\né😀", list.get(5));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "{", "{\"a\" 1}", "{\"a\": 1,}", "[1,]", "[1 2]", "01", "-", "1.", ".5", "1e",
            "\"unterminated", "\"bad \\x escape\"", "\"\\u12\"", "\"\\ud800\"", "\"\\udc00x\"", "tru", "nul",
            "{\"a\": 1, \"a\": 2}", "{a: 1}", "'x'", "[1] [2]", "\"tab\there\"", "NaN", "Infinity", "/* c */ 1"})
    void malformedInputIsRejected(String json) {
        assertThrows(MalformedDefinitionException.class, () -> JsonReader.read(json));
    }

    @Test
    void nestingIsLimited() {
        String deep = "[".repeat(JsonReader.MAX_DEPTH + 1) + "]".repeat(JsonReader.MAX_DEPTH + 1);
        assertThrows(MalformedDefinitionException.class, () -> JsonReader.read(deep));
        String allowed = "[".repeat(JsonReader.MAX_DEPTH) + "]".repeat(JsonReader.MAX_DEPTH);
        assertTrue(assertDoesNotFail(allowed));
    }

    private static boolean assertDoesNotFail(String json) {
        try {
            JsonReader.read(json);
            return true;
        } catch (MalformedDefinitionException e) {
            return false;
        }
    }

    @Test
    void errorsReportPositionsButNotContent() {
        MalformedDefinitionException failure = assertThrows(MalformedDefinitionException.class,
                () -> JsonReader.read("{\n  \"secret-value\": tru\n}"));
        assertTrue(failure.getMessage().contains("line 2"), failure.getMessage());
        assertFalse(failure.getMessage().contains("secret-value"));
    }
}
