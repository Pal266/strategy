package com.pidluzsnij.strategy.config.persistence.toml;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.toml.TomlFormat;
import com.electronwill.nightconfig.toml.TomlParser;
import com.electronwill.nightconfig.toml.TomlVersion;
import com.pidluzsnij.strategy.config.ApplicationSettings;
import com.pidluzsnij.strategy.config.persistence.ConfigurationPersistence;
import com.pidluzsnij.strategy.config.persistence.ConfigurationPersistenceException;
import com.pidluzsnij.strategy.config.persistence.ConfigurationPersistenceException.Operation;
import com.pidluzsnij.strategy.config.persistence.DirectoriesConfigurationLocation;
import com.pidluzsnij.strategy.config.persistence.LoadResult;
import com.pidluzsnij.strategy.testsupport.RecordingStorage;
import com.pidluzsnij.strategy.testsupport.TestSettings;
import dev.dirs.BaseDirectories;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static com.pidluzsnij.strategy.testsupport.TestSettings.COUNT;
import static com.pidluzsnij.strategy.testsupport.TestSettings.ENABLED;
import static com.pidluzsnij.strategy.testsupport.TestSettings.NAME;
import static com.pidluzsnij.strategy.testsupport.TestSettings.RATIO;
import static com.pidluzsnij.strategy.testsupport.TestSettings.SCHEMA;
import static com.pidluzsnij.strategy.testsupport.TestSettings.VOLUME;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** TOML persistence in isolated temporary directories. */
class TomlConfigurationPersistenceTest {

    @TempDir
    Path temp;

    private Path base() {
        return temp.resolve("config");
    }

    private Path file() {
        return base().resolve("strategy").resolve("application-settings.toml");
    }

    private TomlConfigurationPersistence persistence() {
        return new TomlConfigurationPersistence(SCHEMA, base());
    }

    private void writeConfig(String toml) throws IOException {
        Files.createDirectories(file().getParent());
        Files.writeString(file(), toml, StandardCharsets.UTF_8);
    }

    private static ApplicationSettings custom() {
        return ApplicationSettings.defaults(SCHEMA)
                .with(VOLUME, 12).with(NAME, "Žluťoučký kůň \"quoted\"").with(COUNT, 8)
                .with(ENABLED, false).with(RATIO, 0.125);
    }

    static CommentedConfig parseToml10(String text) {
        TomlParser parser = TomlFormat.instance().createParser();
        parser.setTomlVersion(TomlVersion.v1_0);
        return parser.parse(text);
    }

    // --- Format-independent boundary ------------------------------------------------------

    @Test
    void settingsPassThroughTheFormatIndependentBoundary() throws Exception {
        ConfigurationPersistence persistence = persistence();

        persistence.save(custom());
        LoadResult result = persistence.load();

        LoadResult.Loaded loaded = assertInstanceOf(LoadResult.Loaded.class, result);
        assertEquals(custom(), loaded.settings());
        assertEquals(file(), persistence.file());
    }

    // --- TOML round trip ------------------------------------------------------------------

    @Test
    void savedSettingsAreValidToml10AndLoadBack() throws Exception {
        persistence().save(custom());

        String text = Files.readString(file(), StandardCharsets.UTF_8);
        CommentedConfig parsed = parseToml10(text);
        assertEquals(12, parsed.<Number>get("volume").intValue());
        assertEquals("Žluťoučký kůň \"quoted\"", parsed.get("test.name"));
        assertEquals(8, parsed.<Number>get("test.count").intValue());
        assertEquals(false, parsed.get("test.enabled"));
        assertEquals(0.125, parsed.<Number>get("graphics.detail.ratio").doubleValue());

        LoadResult.Loaded loaded = assertInstanceOf(LoadResult.Loaded.class, persistence().load());
        assertEquals(custom(), loaded.settings());
        assertFalse(loaded.normalization().changed());
    }

    // --- Locations ------------------------------------------------------------------------

    @Test
    void productionPathIsDirectoriesConfigDirectoryFollowedByStrategySettingsFile() {
        Path configDirectory = new DirectoriesConfigurationLocation().configDirectory();

        assertEquals(Path.of(BaseDirectories.get().configDir), configDirectory);
        assertEquals(configDirectory.resolve("strategy").resolve("application-settings.toml"),
                TomlConfigurationPersistence.settingsFile(configDirectory));
        assertEquals(configDirectory.resolve("strategy").resolve("application-settings.toml"),
                new TomlConfigurationPersistence(SCHEMA, configDirectory).file());
    }

    @Test
    void explicitLocationConfinesAllIoToTheSuppliedDirectory() throws Exception {
        RecordingStorage storage = new RecordingStorage();
        TomlConfigurationPersistence persistence = new TomlConfigurationPersistence(SCHEMA, base(), storage);
        Path production = TomlConfigurationPersistence.settingsFile(Path.of(BaseDirectories.get().configDir));

        persistence.load();
        persistence.save(custom());
        persistence.load();

        assertFalse(storage.paths.isEmpty());
        for (Path path : storage.paths) {
            assertTrue(path.toAbsolutePath().startsWith(temp), "outside the supplied directory: " + path);
            assertFalse(path.toAbsolutePath().startsWith(production.getParent()), "production path accessed: " + path);
        }
    }

    // --- Load outcomes --------------------------------------------------------------------

    @Test
    void validFileLoadsSuccessfully() throws Exception {
        writeConfig(TestSettings.COMPLETE_VALID_TOML);

        LoadResult.Loaded loaded = assertInstanceOf(LoadResult.Loaded.class, persistence().load());

        assertEquals(file(), loaded.file());
        assertEquals(70, loaded.settings().get(VOLUME));
        assertEquals("custom", loaded.settings().get(NAME));
        assertEquals(7, loaded.settings().get(COUNT));
        assertEquals(false, loaded.settings().get(ENABLED));
        assertEquals(0.25, loaded.settings().get(RATIO));
        assertFalse(loaded.normalization().changed());
    }

    @Test
    void missingFileIsNotFound() throws Exception {
        assertInstanceOf(LoadResult.NotFound.class, persistence().load(), "no strategy directory");

        Files.createDirectories(file().getParent());
        LoadResult.NotFound notFound = assertInstanceOf(LoadResult.NotFound.class, persistence().load());
        assertEquals(file(), notFound.file());
        assertFalse(Files.exists(file()), "loading creates nothing");
    }

    @Test
    void unreadableStorageIsAFailureNotNotFound() throws Exception {
        Files.createDirectories(file());   // a directory where the file is expected cannot be read

        LoadResult.Failed failed = assertInstanceOf(LoadResult.Failed.class, persistence().load());

        assertEquals(Operation.READ, failed.failure().operation());
        assertEquals(file(), failed.failure().file());
        assertInstanceOf(IOException.class, failed.failure().getCause());
    }

    @Test
    void ioErrorWhileReadingIsAFailure() {
        RecordingStorage storage = new RecordingStorage();
        storage.readFailure = new AccessDeniedException(file().toString());

        LoadResult result = new TomlConfigurationPersistence(SCHEMA, base(), storage).load();

        LoadResult.Failed failed = assertInstanceOf(LoadResult.Failed.class, result);
        assertEquals(Operation.READ, failed.failure().operation());
        assertTrue(failed.failure().getMessage().contains(file().toString()));
    }

    @Test
    void malformedTomlIsAFailureWithoutSettings() throws Exception {
        for (String malformed : List.of(
                "test.name = \"SECRET-MARKER-unterminated\n",
                "volume = = 3\n",
                "[test]\ncount = 1\ncount = 2\n",
                "volume 70\n")) {
            writeConfig(malformed);

            LoadResult.Failed failed = assertInstanceOf(LoadResult.Failed.class, persistence().load(), malformed);

            assertEquals(Operation.PARSE, failed.failure().operation());
            assertNull(failed.failure().getCause(), "no NightConfig exception is exposed");
            assertFalse(failed.failure().getMessage().contains("SECRET"), failed.failure().getMessage());
            assertTrue(failed.failure().getMessage().contains(file().toString()));
        }
    }

    @Test
    void invalidUtf8IsAFailure() throws Exception {
        Files.createDirectories(file().getParent());
        Files.write(file(), new byte[]{'v', '=', '"', (byte) 0xC3, (byte) 0x28, '"'});

        LoadResult.Failed failed = assertInstanceOf(LoadResult.Failed.class, persistence().load());
        assertEquals(Operation.PARSE, failed.failure().operation());
    }

    // --- Saving ---------------------------------------------------------------------------

    @Test
    void savedSnapshotContainsEveryRecognizedSettingIncludingDefaults() throws Exception {
        ApplicationSettings settings = ApplicationSettings.defaults(SCHEMA).with(COUNT, 9).with(NAME, "changed");

        persistence().save(settings);

        CommentedConfig parsed = parseToml10(Files.readString(file()));
        assertEquals(50, parsed.<Number>get("volume").intValue(), "default value is persisted");
        assertEquals("changed", parsed.get("test.name"));
        assertEquals(9, parsed.<Number>get("test.count").intValue());
        assertEquals(true, parsed.get("test.enabled"), "default value is persisted");
        assertEquals(0.5, parsed.<Number>get("graphics.detail.ratio").doubleValue(), "default value is persisted");
    }

    @Test
    void saveCreatesTheStrategyDirectoryWithoutLogging() throws Exception {
        Path freshBase = temp.resolve("fresh").resolve("base");
        assertFalse(Files.exists(freshBase));

        new TomlConfigurationPersistence(SCHEMA, freshBase).save(custom());

        assertTrue(Files.isDirectory(freshBase.resolve("strategy")));
        assertTrue(Files.isRegularFile(freshBase.resolve("strategy").resolve("application-settings.toml")));
        assertEquals(custom(), ((LoadResult.Loaded) new TomlConfigurationPersistence(SCHEMA, freshBase).load()).settings());
    }

    @Test
    void directoryCreationFailureIsAPersistenceFailure() throws Exception {
        Path notADirectory = Files.writeString(temp.resolve("plain-file"), "x");

        ConfigurationPersistenceException failure = assertThrows(ConfigurationPersistenceException.class,
                () -> new TomlConfigurationPersistence(SCHEMA, notADirectory).save(custom()));

        assertEquals(Operation.CREATE_DIRECTORY, failure.operation());
        assertInstanceOf(IOException.class, failure.getCause());
    }

    @Test
    void replacementIsAtomicAndLeavesNoIntermediateFiles() throws Exception {
        writeConfig(TestSettings.COMPLETE_VALID_TOML);
        RecordingStorage storage = new RecordingStorage();

        new TomlConfigurationPersistence(SCHEMA, base(), storage).save(custom());

        assertEquals(custom(), ((LoadResult.Loaded) persistence().load()).settings());
        assertEquals(0, storage.events.stream().filter(e -> e.equals("config-write " + file())).count(),
                "the target is never written in place");
        assertEquals(1, storage.count("move"));
        assertTrue(storage.events.get(storage.events.size() - 1).startsWith("config-move " + file()),
                "replacement is the final step: " + storage.events);
        try (Stream<Path> files = Files.list(file().getParent())) {
            assertEquals(List.of(file()), files.toList());
        }
    }

    @Test
    void failedReplacementPreservesTheExistingConfiguration() throws Exception {
        writeConfig(TestSettings.COMPLETE_VALID_TOML);
        byte[] original = Files.readAllBytes(file());
        RecordingStorage storage = new RecordingStorage();
        storage.moveFailure = new IOException("simulated replacement failure");

        ConfigurationPersistenceException failure = assertThrows(ConfigurationPersistenceException.class,
                () -> new TomlConfigurationPersistence(SCHEMA, base(), storage).save(custom()));

        assertEquals(Operation.REPLACE, failure.operation());
        assertArrayEquals(original, Files.readAllBytes(file()));
        assertInstanceOf(LoadResult.Loaded.class, persistence().load(), "original remains readable");
        try (Stream<Path> files = Files.list(file().getParent())) {
            assertEquals(List.of(file()), files.toList(), "temporary file is removed");
        }
    }

    @Test
    void failedWritePreservesTheExistingConfiguration() throws Exception {
        writeConfig(TestSettings.COMPLETE_VALID_TOML);
        byte[] original = Files.readAllBytes(file());
        RecordingStorage storage = new RecordingStorage();
        storage.writeFailure = new IOException("simulated disk full");

        ConfigurationPersistenceException failure = assertThrows(ConfigurationPersistenceException.class,
                () -> new TomlConfigurationPersistence(SCHEMA, base(), storage).save(custom()));

        assertEquals(Operation.WRITE, failure.operation());
        assertArrayEquals(original, Files.readAllBytes(file()));
        assertEquals(0, storage.count("move"));
        try (Stream<Path> files = Files.list(file().getParent())) {
            assertEquals(List.of(file()), files.toList());
        }
    }

    @Test
    void olderConfigurationGainsNewlyRecognizedSettingOnNormalization() throws Exception {
        new TomlConfigurationPersistence(TestSettings.OLDER_SCHEMA, base())
                .save(ApplicationSettings.defaults(TestSettings.OLDER_SCHEMA).with(COUNT, 6));
        assertFalse(Files.readString(file()).contains("ratio"));

        LoadResult.Loaded loaded = assertInstanceOf(LoadResult.Loaded.class, persistence().load());

        assertEquals(List.of("graphics.detail.ratio"), loaded.normalization().missing());
        assertEquals(0.5, loaded.settings().get(RATIO));
        assertEquals(6, loaded.settings().get(COUNT));
    }
}
