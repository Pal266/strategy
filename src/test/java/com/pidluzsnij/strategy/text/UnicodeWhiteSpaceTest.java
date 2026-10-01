package com.pidluzsnij.strategy.text;

import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Whitespace is the Unicode White_Space property. */
class UnicodeWhiteSpaceTest {

    @Test
    void matchesTheUnicodeWhiteSpaceProperty() {
        Pattern whiteSpace = Pattern.compile("\\p{IsWhite_Space}");
        for (int codePoint = 0; codePoint <= Character.MAX_CODE_POINT; codePoint++) {
            assertEquals(whiteSpace.matcher(Character.toString(codePoint)).matches(),
                    UnicodeWhiteSpace.isWhiteSpace(codePoint), Integer.toHexString(codePoint));
        }
    }

    @Test
    void blankSurroundingAndTrim() {
        assertTrue(UnicodeWhiteSpace.isBlank(""));
        assertTrue(UnicodeWhiteSpace.isBlank(" \t 　 "));
        assertFalse(UnicodeWhiteSpace.isBlank(" x "));
        assertFalse(UnicodeWhiteSpace.isBlank("\u001C"), "information separators are not White_Space");
        assertTrue(UnicodeWhiteSpace.hasSurroundingWhiteSpace(" en"));
        assertTrue(UnicodeWhiteSpace.hasSurroundingWhiteSpace("en "));
        assertFalse(UnicodeWhiteSpace.hasSurroundingWhiteSpace("e n"));
        assertFalse(UnicodeWhiteSpace.hasSurroundingWhiteSpace(""));
        assertEquals("a b", UnicodeWhiteSpace.trim(" \ta b  "));
        assertEquals("😀", UnicodeWhiteSpace.trim(" 😀 "));
        assertEquals("", UnicodeWhiteSpace.trim(" 　 "));
    }
}
