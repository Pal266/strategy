package com.pidluzsnij.strategy.testsupport;

import com.pidluzsnij.strategy.localization.LocalizationResources;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Isolated in-memory localization resources. Files can be present, missing, unreadable (throwing an
 * {@link IOException} whose message is a protected marker) or arbitrary bytes; every read is recorded.
 */
public final class FixtureResources implements LocalizationResources {

    /** Message of the exception thrown for unreadable files; must never reach diagnostics. */
    public static final String UNREADABLE_MARKER = "SECRET-UNREADABLE-MARKER";

    /** Default valid metadata containing exactly the four required entries. */
    public static final String METADATA = """
            language_identifier,displayed_name,localization_file
            en,English,english.properties
            uk,Українська,ukrainian.properties
            cs,Čeština,czech.properties
            hu,Magyar,hungarian.properties
            """;

    /** Bytes that are not valid UTF-8. */
    public static final byte[] INVALID_UTF8 = {'k', '=', (byte) 0xC3, (byte) 0x28, '\n'};

    private final Map<String, byte[]> files = new LinkedHashMap<>();
    private final List<String> unreadable = new ArrayList<>();
    public final List<String> reads = Collections.synchronizedList(new ArrayList<>());

    /** Valid metadata and four empty localization files. */
    public FixtureResources() {
        put(METADATA_FILE, METADATA);
        put("english.properties", "");
        put("ukrainian.properties", "");
        put("czech.properties", "");
        put("hungarian.properties", "");
    }

    public FixtureResources put(String fileName, String content) {
        return put(fileName, content.getBytes(StandardCharsets.UTF_8));
    }

    public FixtureResources put(String fileName, byte[] content) {
        unreadable.remove(fileName);
        files.put(fileName, content.clone());
        return this;
    }

    public FixtureResources remove(String fileName) {
        unreadable.remove(fileName);
        files.remove(fileName);
        return this;
    }

    public FixtureResources makeUnreadable(String fileName) {
        unreadable.add(fileName);
        return this;
    }

    /** Applies one of the unavailable states to {@code fileName}. */
    public FixtureResources makeUnavailable(String fileName, Unavailability unavailability) {
        return switch (unavailability) {
            case MISSING -> remove(fileName);
            case UNREADABLE -> makeUnreadable(fileName);
            case INVALID_UTF8 -> put(fileName, INVALID_UTF8);
            case MALFORMED -> put(fileName, "test.key=kept\nno separator here\n");
        };
    }

    /** Ways in which a localization file can be unavailable. */
    public enum Unavailability { MISSING, UNREADABLE, INVALID_UTF8, MALFORMED }

    @Override
    public byte[] read(String fileName) throws IOException {
        reads.add(fileName);
        if (unreadable.contains(fileName)) {
            IOException failure = new IOException(UNREADABLE_MARKER + " cause message");
            failure.addSuppressed(new IllegalStateException(UNREADABLE_MARKER + " suppressed"));
            throw new IOException(UNREADABLE_MARKER + " outer", failure);
        }
        byte[] content = files.get(fileName);
        if (content == null) {
            throw new NoSuchFileException(resourceName(fileName));
        }
        return content.clone();
    }
}
