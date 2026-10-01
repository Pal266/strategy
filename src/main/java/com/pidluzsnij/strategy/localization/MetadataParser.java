package com.pidluzsnij.strategy.localization;

import com.pidluzsnij.strategy.text.UnicodeWhiteSpace;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Parses and validates the UTF-8 CSV language metadata. Any violation rejects the whole metadata;
 * failure messages carry line numbers and fixed descriptions but never record content.
 */
final class MetadataParser {

    static final List<String> HEADER = List.of("language_identifier", "displayed_name", "localization_file");

    /** Entries that every metadata must contain. */
    static final List<LanguageMetadata.Entry> REQUIRED_ENTRIES = List.of(
            entry("en", "English", "english.properties"),
            entry("uk", "Українська", "ukrainian.properties"),
            entry("cs", "Čeština", "czech.properties"),
            entry("hu", "Magyar", "hungarian.properties"));

    static final String LOCALIZATION_FILE_SUFFIX = ".properties";

    private MetadataParser() {
    }

    private static LanguageMetadata.Entry entry(String identifier, String displayName, String file) {
        return new LanguageMetadata.Entry(new Language(identifier, displayName), file);
    }

    static LanguageMetadata parse(String text) throws MalformedResourceException {
        List<String> lines = Utf8.lines(text);
        boolean headerSeen = false;
        List<LanguageMetadata.Entry> entries = new ArrayList<>();
        Set<String> identifiers = new HashSet<>();
        Set<String> files = new HashSet<>();
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            int lineNumber = index + 1;
            if (line.isEmpty()) {
                continue;
            }
            List<String> fields = fields(line, lineNumber);
            if (fields.size() != HEADER.size()) {
                throw new MalformedResourceException(lineNumber,
                        "expected " + HEADER.size() + " fields but found " + fields.size());
            }
            if (!headerSeen) {
                if (!fields.equals(HEADER)) {
                    throw new MalformedResourceException(lineNumber,
                            "header must be " + String.join(",", HEADER));
                }
                headerSeen = true;
                continue;
            }
            for (int field = 0; field < fields.size(); field++) {
                validateValue(fields.get(field), HEADER.get(field), lineNumber);
            }
            String identifier = fields.get(0);
            String file = fields.get(2);
            validateFileName(file, lineNumber);
            if (!identifiers.add(identifier)) {
                throw new MalformedResourceException(lineNumber, "duplicate language identifier");
            }
            if (!files.add(file)) {
                throw new MalformedResourceException(lineNumber, "duplicate localization file name");
            }
            entries.add(entry(identifier, fields.get(1), file));
        }
        if (!headerSeen) {
            throw new MalformedResourceException("header must be " + String.join(",", HEADER));
        }
        for (LanguageMetadata.Entry required : REQUIRED_ENTRIES) {
            if (!entries.contains(required)) {
                throw new MalformedResourceException("required language entry for identifier "
                        + required.language().identifier()
                        + " is missing or has a different display name or localization file");
            }
        }
        return new LanguageMetadata(entries);
    }

    /** Splits one physical line into comma-separated, optionally quoted fields. */
    private static List<String> fields(String line, int lineNumber) throws MalformedResourceException {
        List<String> fields = new ArrayList<>();
        int position = 0;
        while (true) {
            StringBuilder field = new StringBuilder();
            if (position < line.length() && line.charAt(position) == '"') {
                position++;
                while (true) {
                    if (position >= line.length()) {
                        throw new MalformedResourceException(lineNumber, "quoted field is not closed on its line");
                    }
                    char c = line.charAt(position);
                    if (c == '"') {
                        if (position + 1 < line.length() && line.charAt(position + 1) == '"') {
                            field.append('"');
                            position += 2;
                            continue;
                        }
                        position++;
                        break;
                    }
                    field.append(c);
                    position++;
                }
                if (position < line.length() && line.charAt(position) != ',') {
                    throw new MalformedResourceException(lineNumber, "characters follow a closing quote");
                }
            } else {
                while (position < line.length() && line.charAt(position) != ',') {
                    char c = line.charAt(position);
                    if (c == '"') {
                        throw new MalformedResourceException(lineNumber, "quote inside an unquoted field");
                    }
                    field.append(c);
                    position++;
                }
            }
            fields.add(field.toString());
            if (position >= line.length()) {
                return fields;
            }
            position++; // the separating comma
        }
    }

    private static void validateValue(String value, String field, int lineNumber) throws MalformedResourceException {
        if (value.isEmpty()) {
            throw new MalformedResourceException(lineNumber, "field " + field + " is empty");
        }
        if (UnicodeWhiteSpace.hasSurroundingWhiteSpace(value)) {
            throw new MalformedResourceException(lineNumber,
                    "field " + field + " has leading or trailing whitespace");
        }
    }

    private static void validateFileName(String file, int lineNumber) throws MalformedResourceException {
        boolean simple = file.indexOf('/') < 0 && file.indexOf('\\') < 0 && file.indexOf(':') < 0
                && !file.equals(".") && !file.equals("..")
                && file.chars().noneMatch(Character::isISOControl);
        if (!simple) {
            throw new MalformedResourceException(lineNumber,
                    "localization file must be a simple file name within the localization folder");
        }
        if (!file.endsWith(LOCALIZATION_FILE_SUFFIX)) {
            throw new MalformedResourceException(lineNumber,
                    "localization file name must end with " + LOCALIZATION_FILE_SUFFIX);
        }
    }
}
