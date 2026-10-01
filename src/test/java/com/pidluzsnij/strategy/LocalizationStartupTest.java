package com.pidluzsnij.strategy;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.pidluzsnij.strategy.config.ApplicationSettings;
import com.pidluzsnij.strategy.config.LocalizationSettings;
import com.pidluzsnij.strategy.config.VideoResolution;
import com.pidluzsnij.strategy.config.VideoSettings;
import com.pidluzsnij.strategy.config.persistence.LoadResult;
import com.pidluzsnij.strategy.config.persistence.toml.TomlConfigurationPersistence;
import com.pidluzsnij.strategy.localization.LocalizationResources;
import com.pidluzsnij.strategy.logging.FileOperations;
import com.pidluzsnij.strategy.logging.LoggingMode;
import com.pidluzsnij.strategy.testsupport.FakeWindowSystem;
import com.pidluzsnij.strategy.testsupport.FixtureResources;
import com.pidluzsnij.strategy.testsupport.LogHarness;
import com.pidluzsnij.strategy.testsupport.RecordingStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static com.pidluzsnij.strategy.testsupport.LocalizationRun.FAILURE;
import static com.pidluzsnij.strategy.testsupport.LocalizationRun.FALLBACK;
import static com.pidluzsnij.strategy.testsupport.LocalizationRun.LOGGER;
import static com.pidluzsnij.strategy.testsupport.LocalizationRun.SUCCESS;
import static com.pidluzsnij.strategy.testsupport.LogHarness.count;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Localization within application startup: configuration of the language setting, startup order,
 * fatal shutdown and preservation of files, with isolated logging, configuration and resources.
 */
class LocalizationStartupTest {

    private static final String CONFIG_LOGGER = "] com.pidluzsnij.strategy.ConfigurationStartup - ";
    private static final String VIDEO = "[video]\nfullscreen = false\n\n[video.resolution]\nwidth = 1600\nheight = 900\n";

    @TempDir
    Path temp;

    private final List<String> events = Collections.synchronizedList(new ArrayList<>());
    private LogHarness harness;
    private RecordingStorage storage;
    private FixtureResources resources;
    private FakeWindowSystem windowSystem;

    private void prepare(Path directory) {
        harness = new LogHarness(directory);
        storage = new RecordingStorage(events);
        harness.storage = storage;
        resources = new FixtureResources()
                .put("english.properties", "test.key=English\n")
                .put("ukrainian.properties", "test.key=Українська\n");
        harness.localizationResources = recording(resources);
        windowSystem = new FakeWindowSystem(events);
    }

    private void prepare() {
        prepare(temp);
    }

    /** Records every localization resource read into {@link #events}. */
    private LocalizationResources recording(LocalizationResources delegate) {
        return fileName -> {
            events.add("loc-read " + fileName);
            return delegate.read(fileName);
        };
    }

    private void writeConfig(String toml) throws IOException {
        Files.createDirectories(harness.settingsFile().getParent());
        Files.writeString(harness.settingsFile(), toml, StandardCharsets.UTF_8);
    }

    private record Outcome(int exit, List<String> records, String log) {
        List<String> containing(String level, String text) {
            return records.stream().filter(r -> r.contains(" " + level + " [") && r.contains(text)).toList();
        }

        List<String> withLevel(String level) {
            return records.stream().filter(r -> r.contains(" " + level + " [")).toList();
        }
    }

    private Outcome launch(LoggingMode mode) {
        int exit = harness.launch(mode, windowSystem);
        return new Outcome(exit, harness.records(), harness.log());
    }

    private Outcome launch(LoggingMode mode, FileOperations logOperations) {
        int exit = harness.launch(mode, logOperations, harness.location(), windowSystem);
        return new Outcome(exit, harness.records(), harness.log());
    }

    private CommentedConfig savedConfig() throws IOException {
        com.electronwill.nightconfig.toml.TomlParser parser =
                com.electronwill.nightconfig.toml.TomlFormat.instance().createParser();
        parser.setTomlVersion(com.electronwill.nightconfig.toml.TomlVersion.v1_0);
        return parser.parse(Files.readString(harness.settingsFile(), StandardCharsets.UTF_8));
    }

    private int indexOf(String prefix) {
        for (int i = 0; i < events.size(); i++) {
            if (events.get(i).startsWith(prefix)) {
                return i;
            }
        }
        return -1;
    }

    private int indexContaining(String text) {
        for (int i = 0; i < events.size(); i++) {
            if (events.get(i).contains(text)) {
                return i;
            }
        }
        return -1;
    }

    /** Logging file operations that record into {@link #events} and let a test intervene. */
    private FileOperations recordingLogOperations(Consumer<String> onWrite, boolean failRelease) {
        return new FileOperations() {
            @Override
            public FileChannel open(Path file) throws IOException {
                events.add("log-open " + file);
                return FileOperations.super.open(file);
            }

            @Override
            public void write(FileChannel channel, ByteBuffer data) throws IOException {
                String text = StandardCharsets.UTF_8.decode(data.duplicate()).toString();
                try {
                    onWrite.accept(text);
                } catch (IllegalStateException e) {
                    throw new IOException(e.getMessage());
                }
                FileOperations.super.write(channel, data);
                events.add("log-write " + text.strip());
            }

            @Override
            public void release(FileLock lock) throws IOException {
                events.add("log-release");
                FileOperations.super.release(lock);
                if (failRelease) {
                    throw new IOException("simulated ownership-release failure");
                }
            }

            @Override
            public void close(FileChannel channel) throws IOException {
                events.add("log-close");
                FileOperations.super.close(channel);
            }
        };
    }

    private static void assertLogOwnershipReleased(Path logFile) throws IOException {
        try (FileChannel channel = FileChannel.open(logFile, StandardOpenOption.WRITE)) {
            FileLock lock = channel.tryLock(FileOperations.OWNERSHIP_LOCK_POSITION, 1, false);
            assertNotNull(lock, "log ownership was released");
            lock.release();
        }
    }

    private void assertLocalizationSucceeded(Outcome outcome, String effective, boolean fallback) {
        assertEquals(0, outcome.exit(), harness.stderr() + outcome.log());
        List<String> info = outcome.containing("INFO ", SUCCESS);
        assertEquals(1, info.size(), outcome.log());
        assertTrue(info.get(0).contains("effective language '" + effective + "'"), info.get(0));
        assertTrue(info.get(0).contains("English fallback used: " + (fallback ? "yes" : "no")), info.get(0));
        assertTrue(events.contains("initialize"), "window startup follows localization");
    }

    // --- Configuration of the language setting --------------------------------------------

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void defaultLanguageIsCreatedOrAddedToOlderConfiguration(boolean older) throws Exception {
        prepare();
        if (older) {
            writeConfig(VIDEO);
        }

        Outcome outcome = launch(LoggingMode.DEVELOPMENT);

        assertLocalizationSucceeded(outcome, "en", false);
        CommentedConfig saved = savedConfig();
        assertEquals("en", saved.get("localization.language"));
        assertEquals(1, saved.<CommentedConfig>get("localization").size());
        assertTrue(Files.readString(harness.settingsFile()).contains("[localization]\nlanguage = \"en\"\n"));
        if (older) {
            assertEquals(false, saved.get("video.fullscreen"), "unrelated settings are preserved");
            assertEquals(1600, saved.<Number>get("video.resolution.width").intValue());
            assertEquals(900, saved.<Number>get("video.resolution.height").intValue());
            assertEquals(1, outcome.containing("DEBUG", "'localization.language' is missing").size(), outcome.log());
        } else {
            assertEquals(1, outcome.containing("INFO ", "created it with default settings").size());
        }
        assertTrue(outcome.withLevel("WARN ").isEmpty(), outcome.log());
        assertTrue(indexOf("config-move") < indexOf("loc-read languages.csv"), "saved before localization");
    }

    @ParameterizedTest
    @ValueSource(strings = {"true", "42", "1.5", "[\"en\"]", "{ id = \"en\" }", "\"\"", "\"   \"",
            "\" SECRET-LANG\"", "\"SECRET-LANG \"", "\"\\u00A0SECRET-LANG\""})
    void invalidLanguageIsDefaultedAndSavedByConfigurationWithOneWarning(String value) throws Exception {
        for (LoggingMode mode : LoggingMode.values()) {
            prepare(temp.resolve(mode.id()));
            writeConfig(VIDEO + "\n[localization]\nlanguage = " + value + "\n");

            Outcome outcome = launch(mode);

            assertLocalizationSucceeded(outcome, "en", false);
            assertEquals("en", savedConfig().get("localization.language"));
            assertEquals(false, savedConfig().get("video.fullscreen"));
            List<String> warnings = outcome.withLevel("WARN ");
            assertEquals(1, warnings.size(), outcome.log());
            assertTrue(warnings.get(0).contains(CONFIG_LOGGER), warnings.get(0));
            assertTrue(warnings.get(0).contains("'localization.language'"), warnings.get(0));
            assertTrue(warnings.get(0).contains(value.startsWith("\"") ? "failed validation"
                    : "incompatible type; expected string"), warnings.get(0));
            assertFalse(outcome.log().contains("SECRET"), outcome.log());
            assertTrue(outcome.records().stream().filter(r -> r.contains(LOGGER))
                    .noneMatch(r -> r.contains("localization.language") || r.contains(" WARN ")), outcome.log());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"localization = \"SECRET-SCALAR\"\n", "localization = [\"uk\"]\n", "localization = 5\n"})
    void wrongShapedLocalizationSectionIsReplacedWithOneWarning(String section) throws Exception {
        prepare();
        writeConfig(section + VIDEO);

        Outcome outcome = launch(LoggingMode.DEVELOPMENT);

        assertLocalizationSucceeded(outcome, "en", false);
        List<String> warnings = outcome.withLevel("WARN ");
        assertEquals(1, warnings.size(), outcome.log());
        assertTrue(warnings.get(0).contains("'localization.language'"), warnings.get(0));
        assertTrue(warnings.get(0).contains("enclosing section is not a table"), warnings.get(0));
        assertTrue(outcome.containing("DEBUG", "'localization' discarded").isEmpty(), outcome.log());
        assertTrue(outcome.containing("DEBUG", "'localization.language' is missing").isEmpty(), outcome.log());
        CommentedConfig saved = savedConfig();
        assertEquals("en", saved.get("localization.language"));
        assertEquals(false, saved.get("video.fullscreen"), "unrelated recognized settings are unchanged");
        assertEquals(1600, saved.<Number>get("video.resolution.width").intValue());
        assertFalse(outcome.log().contains("SECRET"));
        assertTrue(indexOf("config-move") < indexOf("loc-read languages.csv"), "saved before localization");
    }

    @ParameterizedTest
    @ValueSource(strings = {"SECRET-UNKNOWN-LANGUAGE", "EN"})
    void unknownConfiguredLanguageFallsBackWithoutRewritingThePreference(String configured) throws Exception {
        prepare();
        writeConfig(VIDEO + "\n[localization]\nlanguage = \"" + configured + "\"\n");
        FileTime past = FileTime.fromMillis(1_000_000_000_000L);
        Files.setLastModifiedTime(harness.settingsFile(), past);
        byte[] before = Files.readAllBytes(harness.settingsFile());

        Outcome outcome = launch(LoggingMode.DEVELOPMENT);

        assertLocalizationSucceeded(outcome, "en", true);
        assertEquals(1, outcome.containing("WARN ", FALLBACK).size(), outcome.log());
        assertEquals(1, outcome.withLevel("WARN ").size(), outcome.log());
        assertArrayEquals(before, Files.readAllBytes(harness.settingsFile()), "no fallback-only rewrite");
        assertEquals(past, Files.getLastModifiedTime(harness.settingsFile()));
        assertEquals(0, storage.count("move"));
        assertFalse(outcome.log().contains(configured), "raw identifier is not echoed");
    }

    @Test
    void validPreferenceIsSelectedWithoutRewriteAndSurvivesSaveAndReload() throws Exception {
        prepare();
        writeConfig(VIDEO + "\n[localization]\nlanguage = \"uk\"\n");
        FileTime past = FileTime.fromMillis(1_000_000_000_000L);
        Files.setLastModifiedTime(harness.settingsFile(), past);
        byte[] before = Files.readAllBytes(harness.settingsFile());

        Outcome outcome = launch(LoggingMode.DEVELOPMENT);

        assertLocalizationSucceeded(outcome, "uk", false);
        assertArrayEquals(before, Files.readAllBytes(harness.settingsFile()));
        assertEquals(past, Files.getLastModifiedTime(harness.settingsFile()));
        assertEquals(0, storage.count("move"));
        assertTrue(outcome.withLevel("WARN ").isEmpty(), outcome.log());

        TomlConfigurationPersistence persistence = new TomlConfigurationPersistence(ApplicationSettings.SCHEMA,
                harness.configDirectory);
        ApplicationSettings loaded = assertInstanceOf(LoadResult.Loaded.class, persistence.load()).settings();
        ApplicationSettings changed = loaded.with(VideoSettings.FULLSCREEN, true);
        persistence.save(changed);
        LoadResult.Loaded reloaded = assertInstanceOf(LoadResult.Loaded.class, persistence.load());
        assertFalse(reloaded.normalization().changed());
        assertEquals("uk", reloaded.settings().get(LocalizationSettings.LANGUAGE));
        assertEquals(true, reloaded.settings().get(VideoSettings.FULLSCREEN));
        assertEquals(VideoResolution.of(1600, 900), reloaded.settings().get(VideoSettings.RESOLUTION));

        persistence.save(ApplicationSettings.defaults(ApplicationSettings.SCHEMA));
        assertEquals("en", savedConfig().get("localization.language"), "default-valued language is persisted");
    }

    enum ConfigurationFailure { SAVE_OLDER, SAVE_INVALID, MALFORMED, UNREADABLE }

    @ParameterizedTest
    @EnumSource(ConfigurationFailure.class)
    void configurationFailurePreventsLocalization(ConfigurationFailure failure) throws Exception {
        prepare();
        switch (failure) {
            case SAVE_OLDER -> {
                writeConfig(VIDEO);
                storage.moveFailure = new IOException("simulated replacement failure");
            }
            case SAVE_INVALID -> {
                writeConfig(VIDEO + "\n[localization]\nlanguage = 7\n");
                storage.moveFailure = new IOException("simulated replacement failure");
            }
            case MALFORMED -> writeConfig("[localization\nlanguage = \"uk\"\n");
            case UNREADABLE -> Files.createDirectories(harness.settingsFile());
            default -> throw new AssertionError(failure);
        }
        byte[] before = Files.isRegularFile(harness.settingsFile()) ? Files.readAllBytes(harness.settingsFile()) : null;

        Outcome outcome = launch(LoggingMode.DEVELOPMENT);

        assertEquals(1, outcome.exit());
        assertEquals(1, outcome.containing("ERROR", "Configuration initialization failed").size(), outcome.log());
        assertTrue(events.stream().noneMatch(e -> e.startsWith("loc-read")), events.toString());
        assertTrue(outcome.records().stream().noneMatch(r -> r.contains(LOGGER)), outcome.log());
        assertFalse(harness.infrastructureStarted.get());
        assertFalse(outcome.log().contains("Normal shutdown completed"));
        if (before != null) {
            assertArrayEquals(before, Files.readAllBytes(harness.settingsFile()), "existing configuration is intact");
        }
    }

    // --- Startup order and fatal localization shutdown -------------------------------------

    @Test
    void startupOrderIsLoggingConfigurationLocalizationThenWindow() {
        prepare();

        Outcome outcome = launch(LoggingMode.DEFAULT, recordingLogOperations(text -> { }, false));

        assertLocalizationSucceeded(outcome, "en", false);
        int logOpen = indexOf("log-open");
        int configRead = indexOf("config-read");
        int configured = indexContaining("Configuration initialized");
        int metadata = indexOf("loc-read languages.csv");
        int file = indexOf("loc-read english.properties");
        int localized = indexContaining(SUCCESS);
        int glfw = events.indexOf("initialize");
        assertTrue(logOpen >= 0 && logOpen < configRead, events.toString());
        assertTrue(configRead < configured && configured < metadata, events.toString());
        assertTrue(metadata < file && file < localized && localized < glfw, events.toString());
    }

    @Test
    void earlierFailuresPreventLocalization() {
        prepare(temp.resolve("logging"));
        int exit = harness.launch(LoggingMode.DEFAULT, FileOperations.SYSTEM, () -> {
            throw new IllegalStateException("simulated logging location failure");
        }, windowSystem);
        assertEquals(1, exit);
        assertTrue(events.stream().noneMatch(e -> e.startsWith("loc-read")), events.toString());
        assertFalse(harness.infrastructureStarted.get());

        events.clear();
        prepare(temp.resolve("configuration"));
        harness.configurationLocation = () -> {
            throw new IllegalStateException("simulated configuration location failure");
        };
        Outcome outcome = launch(LoggingMode.DEFAULT);
        assertEquals(1, outcome.exit());
        assertTrue(events.stream().noneMatch(e -> e.startsWith("loc-read")), events.toString());
        assertFalse(harness.infrastructureStarted.get());
    }

    enum Fatal { METADATA, CONFIGURED_ENGLISH, ENGLISH_FALLBACK }

    private void arrange(Fatal fatal) throws IOException {
        switch (fatal) {
            case METADATA -> resources.remove(LocalizationResources.METADATA_FILE);
            case CONFIGURED_ENGLISH -> resources.put("english.properties", "SECRET-NO-SEPARATOR\n");
            case ENGLISH_FALLBACK -> {
                writeConfig(VIDEO + "\n[localization]\nlanguage = \"uk\"\n");
                resources.remove("ukrainian.properties").makeUnreadable("english.properties");
            }
            default -> throw new AssertionError(fatal);
        }
    }

    @ParameterizedTest
    @EnumSource(Fatal.class)
    void fatalLocalizationFailureStopsStartupAndReleasesTheLog(Fatal fatal) throws Exception {
        prepare();
        arrange(fatal);
        byte[] configuration = Files.isRegularFile(harness.settingsFile())
                ? Files.readAllBytes(harness.settingsFile()) : null;

        Outcome outcome = launch(LoggingMode.DEVELOPMENT, recordingLogOperations(text -> { }, false));

        assertEquals(1, outcome.exit());
        assertFalse(harness.infrastructureStarted.get(), "no window system is created");
        assertFalse(events.contains("initialize"), "no GLFW initialization");
        List<String> errors = outcome.withLevel("ERROR");
        assertEquals(1, errors.size(), outcome.log());
        assertEquals(1, count(outcome.log(), FAILURE));
        assertTrue(outcome.containing("INFO ", SUCCESS).isEmpty());
        assertEquals(fatal == Fatal.ENGLISH_FALLBACK ? 1 : 0, outcome.containing("WARN ", FALLBACK).size());
        assertFalse(outcome.log().contains("Normal shutdown completed"));
        assertFalse(outcome.log().contains("SECRET"), outcome.log());
        int error = indexContaining(FAILURE);
        int release = events.indexOf("log-release");
        int close = events.indexOf("log-close");
        assertTrue(error >= 0 && error < release && release < close, events.toString());
        assertEquals(close, events.size() - 1, "nothing is written after logging closes");
        assertLogOwnershipReleased(harness.logFile());
        if (configuration != null) {
            assertArrayEquals(configuration, Files.readAllBytes(harness.settingsFile()), "preference preserved");
        }
    }

    @Test
    void unexpectedJvmErrorDuringLocalizationIsLoggedBeforeLoggingCloses() throws Exception {
        prepare();
        harness.localizationResources = fileName -> {
            events.add("loc-read " + fileName);
            if (fileName.equals("english.properties")) {
                throw new NoClassDefFoundError("simulated missing class");
            }
            return resources.read(fileName);
        };

        Outcome outcome = launch(LoggingMode.DEFAULT, recordingLogOperations(text -> { }, false));

        assertEquals(1, outcome.exit());
        assertFalse(harness.infrastructureStarted.get());
        assertEquals(1, outcome.withLevel("ERROR").size(), outcome.log());
        assertTrue(outcome.withLevel("ERROR").get(0).contains("java.lang.NoClassDefFoundError"));
        assertTrue(harness.stderr().isEmpty(), "nothing escapes to stderr: " + harness.stderr());
        int error = indexContaining(FAILURE);
        int release = events.indexOf("log-release");
        assertTrue(error >= 0 && error < release && release < events.indexOf("log-close"), events.toString());
        assertLogOwnershipReleased(harness.logFile());
    }

    @Test
    void logWriteFailureFallsBackToStderrAndStillReleasesTheLog() throws Exception {
        prepare();
        arrange(Fatal.ENGLISH_FALLBACK);

        Outcome outcome = launch(LoggingMode.DEFAULT, recordingLogOperations(text -> {
            if (text.contains(FAILURE)) {
                throw new IllegalStateException("simulated log write failure");
            }
        }, false));

        assertEquals(1, outcome.exit());
        assertTrue(harness.stderr().contains("Failed to write diagnostic log record"), harness.stderr());
        assertFalse(harness.stderr().contains("SECRET"), harness.stderr());
        assertFalse(outcome.log().contains(FAILURE));
        assertFalse(outcome.log().contains("Normal shutdown completed"));
        assertFalse(harness.infrastructureStarted.get());
        assertTrue(events.contains("log-release"), events.toString());
        assertTrue(events.contains("log-close"), events.toString());
        assertLogOwnershipReleased(harness.logFile());
    }

    @Test
    void failedCleanupOperationDoesNotSuppressLaterCleanup() throws Exception {
        prepare();
        arrange(Fatal.METADATA);

        Outcome outcome = launch(LoggingMode.DEFAULT, recordingLogOperations(text -> { }, true));

        assertEquals(1, outcome.exit());
        assertTrue(harness.stderr().contains("Failed to release ownership of log file"), harness.stderr());
        int release = events.indexOf("log-release");
        int close = events.indexOf("log-close");
        assertTrue(release >= 0 && release < close, "closing is still attempted: " + events);
        assertEquals(1, outcome.withLevel("ERROR").size(), outcome.log());
        assertFalse(outcome.log().contains("Normal shutdown completed"));
        assertLogOwnershipReleased(harness.logFile());
    }

    // --- Preservation and isolation --------------------------------------------------------

    /** Bundled localization resources as compiled onto the runtime class path, with their bytes. */
    private static Map<Path, byte[]> bundledResourceBytes() throws IOException, URISyntaxException {
        Path folder = Path.of(LocalizationResources.class.getClassLoader().getResource("localization").toURI());
        Map<Path, byte[]> bytes = new HashMap<>();
        try (Stream<Path> files = Files.list(folder)) {
            for (Path file : files.toList()) {
                bytes.put(file, Files.readAllBytes(file));
            }
        }
        return bytes;
    }

    @Test
    void localizationPreservesResourcesLogAndConfiguration() throws Exception {
        Map<Path, byte[]> bundledBefore = bundledResourceBytes();
        String[] scenarios = {"success", "fallback", "fatal"};
        for (String scenario : scenarios) {
            events.clear();
            prepare(temp.resolve(scenario));
            writeConfig(VIDEO + "\n[localization]\nlanguage = \"" + (scenario.equals("success") ? "uk" : "cs")
                    + "\"\n");
            resources.remove("czech.properties");
            if (scenario.equals("fatal")) {
                resources.remove("english.properties");
            }
            byte[] configuration = Files.readAllBytes(harness.settingsFile());
            Map<String, byte[]> fixtureBefore = new HashMap<>();
            for (String file : List.of("languages.csv", "english.properties", "ukrainian.properties")) {
                try {
                    fixtureBefore.put(file, resources.read(file));
                } catch (IOException e) {
                    fixtureBefore.put(file, null);
                }
            }
            List<String> ownershipChecks = new ArrayList<>();
            harness.localizationResources = fileName -> {
                try (FileChannel channel = FileChannel.open(harness.logFile(), StandardOpenOption.WRITE)) {
                    assertThrows(OverlappingFileLockException.class,
                            () -> channel.tryLock(FileOperations.OWNERSHIP_LOCK_POSITION, 1, false));
                }
                assertTrue(harness.log().contains("Configuration initialized"), "earlier records are kept");
                ownershipChecks.add(fileName);
                return resources.read(fileName);
            };

            Outcome outcome = launch(LoggingMode.DEFAULT);

            assertEquals(scenario.equals("fatal") ? 1 : 0, outcome.exit(), outcome.log());
            assertFalse(ownershipChecks.isEmpty());
            assertArrayEquals(configuration, Files.readAllBytes(harness.settingsFile()),
                    scenario + ": localization never changes the configuration");
            assertEquals(0, storage.count("move"));
            for (Map.Entry<String, byte[]> entry : fixtureBefore.entrySet()) {
                byte[] now;
                try {
                    now = resources.read(entry.getKey());
                } catch (IOException e) {
                    now = null;
                }
                assertArrayEquals(entry.getValue(), now, entry.getKey());
            }
            assertTrue(outcome.log().contains("Application startup begins"), "log records are preserved");
            assertLogOwnershipReleased(harness.logFile());
        }
        Map<Path, byte[]> bundledAfter = bundledResourceBytes();
        assertEquals(bundledBefore.keySet(), bundledAfter.keySet(), "no bundled resource is created or removed");
        bundledBefore.forEach((file, bytes) -> assertArrayEquals(bytes, bundledAfter.get(file), file.toString()));
        for (Map.Entry<Path, byte[]> entry : bundledAfter.entrySet()) {
            if (entry.getKey().toString().endsWith(".properties")) {
                assertEquals(0, entry.getValue().length, "no test translation entered " + entry.getKey());
            }
        }
    }

    @Test
    void startupFileAccessStaysInIsolatedLocations() throws Exception {
        for (boolean fatal : List.of(false, true)) {
            events.clear();
            Path root = Files.createDirectories(temp.resolve(fatal ? "fatal" : "success"));
            prepare(root);
            if (fatal) {
                resources.remove("english.properties");
            }

            Outcome outcome = launch(LoggingMode.DEFAULT, recordingLogOperations(text -> { }, false));

            assertEquals(fatal ? 1 : 0, outcome.exit());
            List<Path> accessed = new ArrayList<>(storage.paths);
            events.stream().filter(e -> e.startsWith("log-open "))
                    .map(e -> Path.of(e.substring(e.indexOf(' ') + 1))).forEach(accessed::add);
            assertFalse(accessed.isEmpty());
            for (Path path : accessed) {
                assertTrue(path.toAbsolutePath().startsWith(root), "outside the isolated locations: " + path);
            }
            assertTrue(resources.reads.contains("languages.csv"), "the isolated fixture supplied the resources");
        }
    }

    // --- Existing behaviour ----------------------------------------------------------------

    @Test
    void existingWindowBehaviourIsUnchangedWithBundledResources() {
        prepare();
        harness.localizationResources = LocalizationResources.bundled();
        windowSystem.iterationsBeforeClose = 3;

        Outcome outcome = launch(LoggingMode.DEFAULT);

        assertLocalizationSucceeded(outcome, "en", false);
        assertEquals("My strategy", windowSystem.createdSettings.title());
        assertTrue(windowSystem.createdSettings.fullscreen());
        assertEquals(1920, windowSystem.createdResolution.width());
        assertEquals(1080, windowSystem.createdResolution.height());
        assertTrue(windowSystem.framesRendered > 3, "black frames are rendered until close");
        assertTrue(events.indexOf("destroyWindow") < events.indexOf("terminate"));
        assertTrue(outcome.log().contains("Window opened displaying black: title 'My strategy'"));
        assertTrue(outcome.log().contains("Normal shutdown completed"));
        assertEquals(Set.of("video", "localization"), Set.copyOf(savedConfigUnchecked().valueMap().keySet()));
    }

    private CommentedConfig savedConfigUnchecked() {
        try {
            return savedConfig();
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    @Test
    void launcherRequiresTheLanguageSetting() {
        prepare();
        assertThrows(IllegalArgumentException.class, () -> new ApplicationLauncher(LoggingMode.DEFAULT,
                harness.location(), FileOperations.SYSTEM, harness.stderr,
                com.pidluzsnij.strategy.config.SettingsSchema.of(VideoSettings.FULLSCREEN), harness.configurationLocation,
                ApplicationLauncher.TOML_PERSISTENCE, resources, FakeWindowSystem::new,
                () -> LogHarness.KNOWN_RUNTIME));
    }
}
