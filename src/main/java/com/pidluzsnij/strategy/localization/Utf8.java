package com.pidluzsnij.strategy.localization;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Strict UTF-8 decoding and physical-line splitting shared by the localization file formats. */
final class Utf8 {

    private Utf8() {
    }

    /** @throws CharacterCodingException when {@code bytes} are not valid UTF-8 */
    static String decode(byte[] bytes) throws CharacterCodingException {
        return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
    }

    /**
     * Splits text into physical lines terminated by LF or CRLF; the terminator is not part of a line,
     * and a final terminator does not start another line.
     */
    static List<String> lines(String text) {
        List<String> lines = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = text.indexOf('\n', start);
            int next;
            if (end < 0) {
                end = text.length();
                next = end;
            } else {
                next = end + 1;
            }
            int contentEnd = end > start && text.charAt(end - 1) == '\r' && end < text.length() ? end - 1 : end;
            lines.add(text.substring(start, contentEnd));
            start = next;
        }
        return lines;
    }
}
