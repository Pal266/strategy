package com.pidluzsnij.strategy.text;

/**
 * Whitespace checks based on the Unicode {@code White_Space} property.
 * <p>
 * {@link Character#isWhitespace} and {@link String#strip()} use a different definition (they exclude
 * no-break spaces and include some separators), so they must not be used where specifications
 * define whitespace as Unicode {@code White_Space}.
 */
public final class UnicodeWhiteSpace {

    private UnicodeWhiteSpace() {
    }

    /** @return whether {@code codePoint} has the Unicode {@code White_Space} property */
    public static boolean isWhiteSpace(int codePoint) {
        return switch (codePoint) {
            case 0x0009, 0x000A, 0x000B, 0x000C, 0x000D, 0x0020, 0x0085, 0x00A0, 0x1680,
                 0x2000, 0x2001, 0x2002, 0x2003, 0x2004, 0x2005, 0x2006, 0x2007, 0x2008, 0x2009, 0x200A,
                 0x2028, 0x2029, 0x202F, 0x205F, 0x3000 -> true;
            default -> false;
        };
    }

    /** @return whether {@code text} is empty or consists only of whitespace */
    public static boolean isBlank(String text) {
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            if (!isWhiteSpace(codePoint)) {
                return false;
            }
            i += Character.charCount(codePoint);
        }
        return true;
    }

    /** @return whether {@code text} starts or ends with whitespace */
    public static boolean hasSurroundingWhiteSpace(String text) {
        if (text.isEmpty()) {
            return false;
        }
        return isWhiteSpace(text.codePointAt(0)) || isWhiteSpace(text.codePointBefore(text.length()));
    }

    /** @return {@code text} without leading and trailing whitespace */
    public static String trim(String text) {
        int start = 0;
        int end = text.length();
        while (start < end && isWhiteSpace(text.codePointAt(start))) {
            start += Character.charCount(text.codePointAt(start));
        }
        while (end > start && isWhiteSpace(text.codePointBefore(end))) {
            end -= Character.charCount(text.codePointBefore(end));
        }
        return text.substring(start, end);
    }
}
