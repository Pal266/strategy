package com.pidluzsnij.strategy;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.toml.TomlFormat;
import com.electronwill.nightconfig.toml.TomlParser;
import com.electronwill.nightconfig.toml.TomlVersion;
import com.pidluzsnij.strategy.config.ApplicationSettings;
import com.pidluzsnij.strategy.config.LocalizationSettings;
import com.pidluzsnij.strategy.config.SettingsSchema;
import com.pidluzsnij.strategy.config.persistence.ConfigurationLocation;
import com.pidluzsnij.strategy.config.persistence.StorageOperations;
import com.pidluzsnij.strategy.config.persistence.toml.TomlConfigurationPersistence;
import com.pidluzsnij.strategy.logging.FileOperations;
import com.pidluzsnij.strategy.logging.LoggingMode;
import com.pidluzsnij.strategy.logging.LoggingSystem;
import com.pidluzsnij.strategy.testsupport.FakeWindowSystem;
import com.pidluzsnij.strategy.testsupport.LogHarness;
import com.pidluzsnij.strategy.testsupport.RecordingStorage;
import com.pidluzsnij.strategy.testsupport.TestSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Optional;

import static com.pidluzsnij.strategy.testsupport.LogHarness.count;
import static com.pidluzsnij.strategy.testsupport.TestSettings.COUNT;
import static com.pidluzsnij.strategy.testsupport.TestSettings.ENABLED;
import static com.pidluzsnij.strategy.testsupport.TestSettings.NAME;
import static com.pidluzsnij.strategy.testsupport.TestSettings.RATIO;
import static com.pidluzsnij.strategy.testsupport.TestSettings.SCHEMA;
import static com.pidluzsnij.strategy.testsupport.TestSettings.VOLUME;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Configuration initialization policy, its outcomes on startup, and its diagnostic events. */
class ConfigurationStartupTest {

    private static final String CONFIG_LOGGER = "] com.pidluzsnij.strategy.ConfigurationStartup - ";

    @TempDir
    Path temp;

    private LogHarness harness;
    private RecordingStorage storage;

    private Path settingsFile() {
        return harness.settingsFile();
    }

    private void writeConfig(String toml) throws IOException {
        Files.createDirectories(settingsFile().getParent());
        Files.writeString(settingsFile(), toml, StandardCharsets.UTF_8);
    }

    private record Outcome(Optional<ApplicationSettings> settings, List<String> records, String log) {
        List<String> config(String level) {
            return records.stream().filter(r -> r.contains(" " + level) && r.contains(CONFIG_LOGGER)).toList();
        }

        List<String> withLevel(String level) {
            return records.stream().filter(r -> r.contains(" " + level + " [")).toList();
        }

        boolean has(String level, String... texts) {
            return config(level).stream().anyMatch(r -> List.of(texts).stream().allMatch(r::contains));
        }
    }

    private void prepare() {
        harness = new LogHarness(temp);
        storage = new RecordingStorage();
        harness.settingsSchema = SCHEMA;
        harness.storage = storage;
    }

    /** Runs configuration initialization alone, with logging initialized in {@code mode}. */
    private Outcome initialize(LoggingMode mode) throws Exception {
        Optional<ApplicationSettings> settings;
        LoggingSystem logging = LoggingSystem.initialize(mode, harness.location(), FileOperations.SYSTEM,
                harness.stderr);
        try {
            StorageOperations configStorage = harness.storage;
            settings = new ConfigurationStartup(harness.settingsSchema, harness.configurationLocation,
                    (schema, dir) -> new TomlConfigurationPersistence(schema, dir, configStorage)).initialize();
        } finally {
            logging.close();
        }
        return new Outcome(settings, harness.records(), harness.log());
    }

    /** Runs the full application startup with a fake window system. */
    private Outcome launch(LoggingMode mode, int expectedExit) {
        int exit = harness.launch(mode, new FakeWindowSystem());
        assertEquals(expectedExit, exit, harness.stderr());
        return new Outcome(Optional.empty(), harness.records(), harness.log());
    }

    private static CommentedConfig parse(Path file) throws IOException {
        TomlParser parser = TomlFormat.instance().createParser();
        parser.setTomlVersion(TomlVersion.v1_0);
        return parser.parse(Files.readString(file, StandardCharsets.UTF_8));
    }

    private static void assertCompleteSnapshot(Path file, ApplicationSettings expected) throws IOException {
        CommentedConfig persisted = parse(file);
        assertEquals(expected.get(VOLUME), persisted.<Number>get("volume").intValue());
        assertEquals(expected.get(NAME), persisted.get("test.name"));
        assertEquals(expected.get(COUNT), persisted.<Number>get("test.count").intValue());
        assertEquals(expected.get(ENABLED), persisted.get("test.enabled"));
        assertEquals(expected.get(RATIO), persisted.<Number>get("graphics.detail.ratio").doubleValue());
        assertEquals(java.util.Set.of("volume", "test", "graphics", "localization"),
                java.util.Set.copyOf(persisted.valueMap().keySet()), "no unknown settings are persisted");
        assertEquals(expected.get(LocalizationSettings.LANGUAGE), persisted.get("localization.language"));
        assertEquals(1, persisted.<CommentedConfig>get("localization").size());
        assertEquals(3, persisted.<CommentedConfig>get("test").size());
        assertEquals(1, persisted.<CommentedConfig>get("graphics.detail").size());
    }

    // --- First run ------------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(LoggingMode.class)
    void firstRunCreatesDefaultConfigurationAndSucceeds(LoggingMode mode) throws Exception {
        prepare();
        assertFalse(Files.exists(settingsFile()));

        Outcome outcome = initialize(mode);

        ApplicationSettings defaults = ApplicationSettings.defaults(SCHEMA);
        assertEquals(Optional.of(defaults), outcome.settings());
        assertTrue(Files.isDirectory(settingsFile().getParent()));
        assertCompleteSnapshot(settingsFile(), defaults);

        assertTrue(outcome.has("INFO ", "created", settingsFile().toString()), outcome.records().toString());
        assertTrue(outcome.has("INFO ", "Configuration initialized", "ready for subsequent startup"));
        assertTrue(outcome.withLevel("WARN ").isEmpty());
        assertTrue(outcome.withLevel("ERROR").isEmpty());
        if (mode == LoggingMode.DEFAULT) {
            assertTrue(outcome.withLevel("DEBUG").isEmpty(), outcome.records().toString());
        } else {
            assertTrue(outcome.has("DEBUG", "location resolved", settingsFile().toString()));
            assertTrue(outcome.has("DEBUG", "snapshot saved", settingsFile().toString(), "first-run creation"));
            assertEquals(2, outcome.config("DEBUG").size(), outcome.records().toString());
        }
        String log = outcome.log();
        assertTrue(log.indexOf("snapshot saved") <= log.indexOf("created it with default settings")
                || mode == LoggingMode.DEFAULT);
        assertTrue(log.indexOf("created it with default settings") < log.indexOf("Configuration initialized"),
                "success is logged after persistence");
    }

    @Test
    void firstRunStartsTheApplicationAfterConfiguration() throws Exception {
        prepare();
        // The window needs the production video settings, so this run uses the production schema.
        harness.settingsSchema = ApplicationSettings.SCHEMA;
        launch(LoggingMode.DEFAULT, 0);
        assertTrue(harness.infrastructureStarted.get());
        CommentedConfig persisted = parse(settingsFile());
        assertEquals(true, persisted.get("video.fullscreen"));
        assertEquals("auto", persisted.get("video.resolution.width"));
        assertEquals("auto", persisted.get("video.resolution.height"));
    }

    // --- Fatal failures -------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(LoggingMode.class)
    void malformedConfigurationIsFatalAndNotOverwritten(LoggingMode mode) throws Exception {
        prepare();
        writeConfig("[test]\nname = \"unterminated\n");
        byte[] original = Files.readAllBytes(settingsFile());

        Outcome outcome = launch(mode, 1);

        assertFalse(harness.infrastructureStarted.get(), "startup does not continue");
        assertArrayEquals(original, Files.readAllBytes(settingsFile()));
        assertEquals(0, storage.count("createTempFile") + storage.count("move"), "nothing is saved");
        assertSingleFailure(outcome, "parse configuration file", settingsFile().toString());
    }

    @Test
    void unreadableConfigurationIsFatal() throws Exception {
        prepare();
        Files.createDirectories(settingsFile());

        Outcome outcome = launch(LoggingMode.DEVELOPMENT, 1);

        assertFalse(harness.infrastructureStarted.get());
        assertTrue(Files.isDirectory(settingsFile()), "existing storage is not replaced");
        assertSingleFailure(outcome, "read configuration file", settingsFile().toString());
    }

    @ParameterizedTest
    @EnumSource(LoggingMode.class)
    void directoryCreationFailureIsFatal(LoggingMode mode) throws Exception {
        prepare();
        storage.createDirectoriesFailure = new java.nio.file.AccessDeniedException("simulated: strategy directory");

        Outcome outcome = launch(mode, 1);

        assertFalse(harness.infrastructureStarted.get());
        assertFalse(Files.exists(settingsFile()));
        assertSingleFailure(outcome, "create configuration directory", settingsFile().toString());
    }

    @ParameterizedTest
    @EnumSource(LoggingMode.class)
    void firstRunSaveFailureIsFatal(LoggingMode mode) throws Exception {
        prepare();
        storage.moveFailure = new IOException("simulated replacement failure");

        Outcome outcome = launch(mode, 1);

        assertFalse(harness.infrastructureStarted.get());
        assertFalse(Files.exists(settingsFile()));
        assertSingleFailure(outcome, "replace configuration file", settingsFile().toString());
        assertFalse(outcome.log().contains("created it with default settings"));
        assertFalse(outcome.log().contains("snapshot saved"));
    }

    @Test
    void normalizationSaveFailureIsFatalAndPreservesTheFile() throws Exception {
        prepare();
        writeConfig("volume = 70\n");
        byte[] original = Files.readAllBytes(settingsFile());
        storage.writeFailure = new IOException("simulated disk full");

        Outcome outcome = launch(LoggingMode.DEVELOPMENT, 1);

        assertFalse(harness.infrastructureStarted.get());
        assertArrayEquals(original, Files.readAllBytes(settingsFile()));
        assertSingleFailure(outcome, "write configuration file", settingsFile().toString());
        assertFalse(outcome.log().contains("snapshot saved"));
    }

    @ParameterizedTest
    @EnumSource(LoggingMode.class)
    void locationResolutionFailureIsFatal(LoggingMode mode) {
        prepare();
        harness.configurationLocation = () -> {
            throw new IllegalStateException("simulated location failure");
        };

        Outcome outcome = launch(mode, 1);

        assertFalse(harness.infrastructureStarted.get());
        assertSingleFailure(outcome, "resolve configuration location", "configuration file: unavailable");
        assertTrue(storage.events.isEmpty());
    }

    private void assertSingleFailure(Outcome outcome, String operation, String path) {
        List<String> errors = outcome.withLevel("ERROR");
        assertEquals(1, errors.size(), outcome.records().toString());
        String error = errors.get(0);
        assertTrue(error.contains(CONFIG_LOGGER + "Configuration initialization failed"), error);
        assertTrue(error.contains(operation), error);
        assertTrue(error.contains(path), error);
        assertTrue(error.contains("\tat "), "stack trace: " + error);
        assertEquals(1, count(outcome.log(), "Configuration initialization failed"));
        assertFalse(outcome.log().contains("Configuration initialized"));
        assertFalse(outcome.log().contains("Normal shutdown completed"));
        assertFalse(outcome.log().contains("Window opened"));
    }

    // --- Normalization --------------------------------------------------------------------

    @Test
    void missingRecognizedValueReceivesDefaultAndIsPersisted() throws Exception {
        prepare();
        writeConfig("""
                volume = 70
                [test]
                name = "custom"
                enabled = false
                [graphics.detail]
                ratio = 0.25
                [localization]
                language = "en"
                """);

        Outcome outcome = initialize(LoggingMode.DEVELOPMENT);

        ApplicationSettings settings = outcome.settings().orElseThrow();
        assertEquals(3, settings.get(COUNT));
        assertEquals("custom", settings.get(NAME));
        assertCompleteSnapshot(settingsFile(), settings);
        assertTrue(outcome.has("DEBUG", "'test.count'", "missing"));
        assertTrue(outcome.has("DEBUG", "1 missing value(s) defaulted", "0 invalid value(s) defaulted",
                "0 unknown setting(s) discarded"));
        assertTrue(outcome.has("DEBUG", "snapshot saved", settingsFile().toString(), "normalization"));
    }

    @ParameterizedTest
    @EnumSource(LoggingMode.class)
    void incompatibleTypeReceivesDefaultAndIsPersisted(LoggingMode mode) throws Exception {
        prepare();
        writeConfig(TestSettings.COMPLETE_VALID_TOML.replace("count = 7", "count = \"seven\""));

        Outcome outcome = initialize(mode);

        ApplicationSettings settings = outcome.settings().orElseThrow();
        assertEquals(3, settings.get(COUNT));
        assertEquals("custom", settings.get(NAME));
        assertCompleteSnapshot(settingsFile(), settings);
        List<String> warnings = outcome.withLevel("WARN ");
        assertEquals(1, warnings.size(), outcome.records().toString());
        assertTrue(warnings.get(0).contains("'test.count'"));
        assertTrue(warnings.get(0).contains("incompatible type"));
        assertTrue(outcome.has("INFO ", "Configuration initialized"));
        assertEquals(mode == LoggingMode.DEFAULT, outcome.withLevel("DEBUG").isEmpty());
    }

    @ParameterizedTest
    @EnumSource(LoggingMode.class)
    void validationFailureReceivesDefaultAndIsPersisted(LoggingMode mode) throws Exception {
        prepare();
        writeConfig(TestSettings.COMPLETE_VALID_TOML.replace("count = 7", "count = 11"));

        Outcome outcome = initialize(mode);

        ApplicationSettings settings = outcome.settings().orElseThrow();
        assertEquals(3, settings.get(COUNT));
        assertCompleteSnapshot(settingsFile(), settings);
        List<String> warnings = outcome.withLevel("WARN ");
        assertEquals(1, warnings.size(), outcome.records().toString());
        assertTrue(warnings.get(0).contains("'test.count'"));
        assertTrue(warnings.get(0).contains("failed validation"));
        assertEquals(mode == LoggingMode.DEFAULT, outcome.withLevel("DEBUG").isEmpty());
    }

    @Test
    void unknownSettingIsDiscardedAndRemovedFromThePersistedSnapshot() throws Exception {
        prepare();
        writeConfig(TestSettings.COMPLETE_VALID_TOML + "\n[mods]\nfavourite = \"x\"\n");

        Outcome outcome = initialize(LoggingMode.DEVELOPMENT);

        ApplicationSettings settings = outcome.settings().orElseThrow();
        assertEquals(7, settings.get(COUNT));
        assertFalse(settings.toPersisted().root().containsKey("mods"));
        assertCompleteSnapshot(settingsFile(), settings);
        assertFalse(Files.readString(settingsFile()).contains("mods"));
        assertTrue(outcome.has("DEBUG", "Unknown configuration setting 'mods.favourite' discarded"));
        assertTrue(outcome.has("DEBUG", "0 missing", "0 invalid", "1 unknown"));
        assertTrue(outcome.withLevel("WARN ").isEmpty());
    }

    @Test
    void combinationIsNormalizedToACompleteSnapshot() throws Exception {
        prepare();
        writeConfig("""
                volume = 70
                legacy = true
                [test]
                name = 5
                count = 99
                [graphics.detail]
                ratio = 0.75
                extra = "x"
                [localization]
                language = "en"
                """);

        Outcome outcome = initialize(LoggingMode.DEVELOPMENT);

        ApplicationSettings settings = outcome.settings().orElseThrow();
        ApplicationSettings expected = ApplicationSettings.defaults(SCHEMA).with(VOLUME, 70).with(RATIO, 0.75);
        assertEquals(expected, settings);
        assertCompleteSnapshot(settingsFile(), expected);
        assertTrue(outcome.has("DEBUG", "'test.enabled'", "missing"));
        assertTrue(outcome.has("WARN ", "'test.name'", "incompatible type"));
        assertTrue(outcome.has("WARN ", "'test.count'", "failed validation"));
        assertTrue(outcome.has("DEBUG", "'legacy' discarded"));
        assertTrue(outcome.has("DEBUG", "'graphics.detail.extra' discarded"));
        assertTrue(outcome.has("DEBUG", "1 missing value(s) defaulted", "2 invalid value(s) defaulted",
                "2 unknown setting(s) discarded"));
    }

    @Test
    void olderConfigurationGainsNewlyRecognizedSetting() throws Exception {
        prepare();
        new TomlConfigurationPersistence(TestSettings.OLDER_SCHEMA, harness.configDirectory)
                .save(ApplicationSettings.defaults(TestSettings.OLDER_SCHEMA).with(COUNT, 6));

        Outcome outcome = initialize(LoggingMode.DEVELOPMENT);

        ApplicationSettings settings = outcome.settings().orElseThrow();
        assertEquals(0.5, settings.get(RATIO));
        assertEquals(6, settings.get(COUNT));
        assertCompleteSnapshot(settingsFile(), settings);
        assertTrue(outcome.has("DEBUG", "'graphics.detail.ratio'", "missing"));
    }

    @ParameterizedTest
    @EnumSource(LoggingMode.class)
    void unchangedConfigurationIsNotRewritten(LoggingMode mode) throws Exception {
        prepare();
        writeConfig(TestSettings.COMPLETE_VALID_TOML);
        FileTime past = FileTime.fromMillis(1_000_000_000_000L);
        Files.setLastModifiedTime(settingsFile(), past);
        byte[] original = Files.readAllBytes(settingsFile());

        Outcome outcome = initialize(mode);

        ApplicationSettings settings = outcome.settings().orElseThrow();
        assertEquals(70, settings.get(VOLUME));
        assertEquals(0.25, settings.get(RATIO));
        assertArrayEquals(original, Files.readAllBytes(settingsFile()));
        assertEquals(past, Files.getLastModifiedTime(settingsFile()));
        assertEquals(0, storage.count("createDirectories") + storage.count("createTempFile") + storage.count("move"));
        assertTrue(outcome.has("INFO ", "Configuration initialized"));
        assertTrue(outcome.withLevel("WARN ").isEmpty());
        if (mode == LoggingMode.DEFAULT) {
            assertTrue(outcome.withLevel("DEBUG").isEmpty());
        } else {
            assertTrue(outcome.has("DEBUG", "loaded from", settingsFile().toString()));
            assertTrue(outcome.has("DEBUG", "requires no rewrite", settingsFile().toString()));
            assertFalse(outcome.log().contains("snapshot saved"));
            assertFalse(outcome.log().contains("normalization changed"));
            assertEquals(3, outcome.config("DEBUG").size(), outcome.records().toString());
        }
    }

    // --- Diagnostic value protection ------------------------------------------------------

    @ParameterizedTest
    @EnumSource(LoggingMode.class)
    void diagnosticsNeverContainConfigurationValues(LoggingMode mode) throws Exception {
        String longMarker = "SECRET-VALIDATION-MARKER-" + "x".repeat(40);
        List<String> configurations = List.of(
                "volume = \"SECRET-TYPE-MARKER\"\n[test]\nname = \"SECRET-VALID-MARKER\"\n",
                "[test]\nname = \"" + longMarker + "\"\nunknown = \"SECRET-UNKNOWN-MARKER\"\n",
                "[test]\nname = \"SECRET-MALFORMED-MARKER\n");
        for (int i = 0; i < configurations.size(); i++) {
            Path caseDirectory = Files.createDirectories(temp.resolve("case-" + mode + "-" + i));
            harness = new LogHarness(caseDirectory);
            storage = new RecordingStorage();
            harness.settingsSchema = SCHEMA;
            harness.storage = storage;
            writeConfig(configurations.get(i));

            Outcome outcome = initialize(mode);

            assertFalse(outcome.log().contains("SECRET"), outcome.log());
            assertFalse(harness.stderr().contains("SECRET"), harness.stderr());
            assertFalse(outcome.log().contains("[test]"), "no TOML contents are logged");
            assertFalse(outcome.log().contains("name ="), "no TOML contents are logged");
            assertFalse(outcome.log().contains("ApplicationSettings["), "no snapshots are logged");
        }
    }

    @Test
    void identifiersAndFailureContextAreRetainedWithoutValues() throws Exception {
        prepare();
        writeConfig("volume = \"SECRET-TYPE-MARKER\"\n");

        Outcome outcome = initialize(LoggingMode.DEFAULT);

        assertTrue(outcome.has("WARN ", "'volume'", "incompatible type; expected integer"));
        assertFalse(outcome.log().contains("SECRET"));
    }

    // --- Scope ----------------------------------------------------------------------------

    @Test
    void productionSchemaFirstRunPersistsTheDefaultVideoAndLocalizationSettings() throws Exception {
        harness = new LogHarness(temp);
        assertEquals(ApplicationSettings.SCHEMA, harness.settingsSchema);
        SettingsSchema production = harness.settingsSchema;
        assertEquals(3, production.settings().size());

        FakeWindowSystem windowSystem = new FakeWindowSystem();
        assertEquals(0, harness.launch(LoggingMode.DEFAULT, windowSystem), harness.stderr());

        CommentedConfig persisted = parse(harness.settingsFile());
        assertEquals(java.util.Set.of("video", "localization"), java.util.Set.copyOf(persisted.valueMap().keySet()));
        assertEquals("en", persisted.get("localization.language"));
        assertEquals(true, persisted.get("video.fullscreen"));
        assertEquals("auto", persisted.get("video.resolution.width"));
        assertEquals("auto", persisted.get("video.resolution.height"));
        assertEquals("My strategy", windowSystem.createdSettings.title());
        assertTrue(windowSystem.createdSettings.fullscreen());
        assertEquals(1920, windowSystem.createdResolution.width());
        assertEquals(1080, windowSystem.createdResolution.height());
    }

    @Test
    void locationIsOnlyResolvedThroughTheSuppliedLocation() throws Exception {
        prepare();
        ConfigurationLocation supplied = harness.configurationLocation;
        int[] calls = {0};
        harness.configurationLocation = () -> {
            calls[0]++;
            return supplied.configDirectory();
        };

        initialize(LoggingMode.DEFAULT);

        assertEquals(1, calls[0]);
    }

    // --- Review follow-ups ----------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(LoggingMode.class)
    void missingConfigurationLibraryIsAFatalConfigurationFailure(LoggingMode mode) {
        prepare();
        int exit = new ApplicationLauncher(mode, harness.location(), FileOperations.SYSTEM, harness.stderr, SCHEMA,
                harness.configurationLocation, (schema, dir) -> {
                    throw new NoClassDefFoundError("com/electronwill/nightconfig/core/UnmodifiableConfig");
                }, () -> {
                    harness.infrastructureStarted.set(true);
                    return new FakeWindowSystem();
                }, () -> LogHarness.KNOWN_RUNTIME).launch();

        assertEquals(1, exit);
        Outcome outcome = new Outcome(Optional.empty(), harness.records(), harness.log());
        assertFalse(harness.infrastructureStarted.get());
        assertSingleFailure(outcome, "create configuration persistence", "configuration file: unavailable");
        assertTrue(outcome.log().contains("java.lang.NoClassDefFoundError"));
        assertFalse(outcome.log().contains("resolve configuration location"));
    }

    @Test
    void unknownIdentifiersCannotForgeLogRecords() throws Exception {
        prepare();
        writeConfig(TestSettings.COMPLETE_VALID_TOML
                + "\n[\"we\\nird\"]\nk = 1\n\"tab\\there\" = 2\n\"back\\\\slash\\u2028\" = 3\n");

        Outcome outcome = initialize(LoggingMode.DEVELOPMENT);

        assertTrue(outcome.settings().isPresent());
        assertTrue(outcome.has("DEBUG", "Unknown configuration setting 'we\\u000Aird.k' discarded"),
                outcome.log());
        assertTrue(outcome.has("DEBUG", "Unknown configuration setting 'we\\u000Aird.tab\\u0009here' discarded"));
        assertTrue(outcome.has("DEBUG", "Unknown configuration setting 'we\\u000Aird.back\\\\slash\\u2028' discarded"));
        for (String line : outcome.log().split("\n", -1)) {
            assertTrue(line.isEmpty() || LogHarness.RECORD_START.matcher(line).matches(),
                    "every line is a genuine record: " + line);
        }
    }

    @Test
    void printableEscapesOnlyAmbiguousCharacters() {
        assertEquals("graphics.detail.ratio", ConfigurationStartup.printable("graphics.detail.ratio"));
        assertEquals("Žluť klíč", ConfigurationStartup.printable("Žluť klíč"));
        assertEquals("a\\u000Db\\u001Bc\\u0085d\\u2029e\\\\f",
                ConfigurationStartup.printable("a\rb\u001Bc\u0085d\u2029e\\f"));
    }
}
