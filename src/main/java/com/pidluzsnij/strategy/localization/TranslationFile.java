package com.pidluzsnij.strategy.localization;

import com.pidluzsnij.strategy.text.UnicodeWhiteSpace;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parsed localization file: a line-oriented subset of properties syntax. Blank lines and lines whose
 * first non-whitespace character is {@code #} are ignored; every other line is {@code key=value},
 * split at the first {@code =}. Keys are trimmed and must be nonempty and unique; values are kept
 * exactly. No escapes, continuations, inline comments or placeholders are interpreted.
 */
final class TranslationFile {

    private final Map<String, String> usableValues;
    private final int keyCount;

    private TranslationFile(Map<String, String> usableValues, int keyCount) {
        this.usableValues = Map.copyOf(usableValues);
        this.keyCount = keyCount;
    }

    /** @throws MalformedResourceException when any record is invalid; nothing is partially retained */
    static TranslationFile parse(String text) throws MalformedResourceException {
        List<String> lines = Utf8.lines(text);
        Map<String, String> values = new HashMap<>();
        Map<String, String> usable = new HashMap<>();
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            int lineNumber = index + 1;
            String trimmed = UnicodeWhiteSpace.trim(line);
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int separator = line.indexOf('=');
            if (separator < 0) {
                throw new MalformedResourceException(lineNumber, "record has no '=' separator");
            }
            String key = UnicodeWhiteSpace.trim(line.substring(0, separator));
            String value = line.substring(separator + 1);
            if (key.isEmpty()) {
                throw new MalformedResourceException(lineNumber, "record has an empty key");
            }
            if (values.putIfAbsent(key, value) != null) {
                throw new MalformedResourceException(lineNumber, "duplicate key");
            }
            if (!UnicodeWhiteSpace.isBlank(value)) {
                usable.put(key, value);
            }
        }
        return new TranslationFile(usable, values.size());
    }

    /** @return keys mapped to their nonempty, non-whitespace-only values */
    Map<String, String> usableValues() {
        return usableValues;
    }

    /** @return the number of keys in the file, including keys without a usable value */
    int keyCount() {
        return keyCount;
    }
}
