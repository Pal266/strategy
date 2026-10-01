package com.pidluzsnij.strategy;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.toml.TomlFormat;
import com.electronwill.nightconfig.toml.TomlParser;
import com.electronwill.nightconfig.toml.TomlVersion;
import com.pidluzsnij.strategy.config.ApplicationSettings;
import com.pidluzsnij.strategy.config.VideoResolution;
import com.pidluzsnij.strategy.config.VideoSettings;
import com.pidluzsnij.strategy.config.persistence.StorageOperations;
import com.pidluzsnij.strategy.config.persistence.toml.TomlConfigurationPersistence;
import com.pidluzsnij.strategy.logging.FileOperations;
import com.pidluzsnij.strategy.logging.LoggingMode;
import com.pidluzsnij.strategy.logging.LoggingSystem;
import com.pidluzsnij.strategy.testsupport.FakeWindowSystem;
import com.pidluzsnij.strategy.testsupport.LogHarness;
import com.pidluzsnij.strategy.testsupport.RecordingStorage;
import com.pidluzsnij.strategy.window.MonitorInfo;
import com.pidluzsnij.strategy.window.Resolution;
import com.pidluzsnij.strategy.window.VideoMode;
import com.pidluzsnij.strategy.window.WindowState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;

import java.io.IOException;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static com.pidluzsnij.strategy.testsupport.LogHarness.count;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Video settings through configuration initialization and window startup, with isolated
 * configuration and logging paths.
 */
class VideoSettingsStartupTest {

    private static final String CONFIG_LOGGER = "] com.pidluzsnij.strategy.ConfigurationStartup - ";
    private static final String WINDOW_LOGGER = "] com.pidluzsnij.strategy.window.Application - ";
    private static final String SELECTION_EVENT = "Effective video settings selected for startup";
    private static final String RESOLVED_EVENT = "Startup resolution resolved";
    private static final String FALLBACK_WARNING = "Starting monitor resolution could not be determined";

    private static final MonitorInfo MONITOR_2560 = new MonitorInfo("Probe Monitor", new VideoMode(2560, 1440, 144), null);
    private static final MonitorInfo NO_MONITOR = MonitorInfo.unavailable("simulated no video mode");

    /** Invalid resolution representations, each with a detectable fragment of its WARN reason. */
    private static final List<String> INVALID_RESOLUTIONS = List.of(
            "width = 1600\nheight = \"auto\"\n",
            "width = \"auto\"\nheight = 900\n",
            "width = 0\nheight = 900\n",
            "width = 1600\nheight = -900\n",
            "width = \"big\"\nheight = 900\n",
            "width = 1600.5\nheight = 900\n",
            "width = true\nheight = \"auto\"\n",
            "width = 1600\n");

    @TempDir
    Path temp;

    private LogHarness harness;
    private RecordingStorage storage;

    private record Outcome(Optional<ApplicationSettings> settings, List<String> records, String log) {
        List<String> withLevel(String level) {
            return records.stream().filter(r -> r.contains(" " + level + " [")).toList();
        }

        List<String> containing(String level, String text) {
            return withLevel(level).stream().filter(r -> r.contains(text)).toList();
        }
    }

    private void prepare(Path directory) {
        harness = new LogHarness(directory);
        storage = new RecordingStorage();
        harness.storage = storage;
    }

    private void prepare() {
        prepare(temp);
    }

    private void writeConfig(String toml) throws IOException {
        Files.createDirectories(harness.settingsFile().getParent());
        Files.writeString(harness.settingsFile(), toml, StandardCharsets.UTF_8);
    }

    private static String videoToml(String fullscreen, String resolutionTable) {
        return "[video]\nfullscreen = " + fullscreen + "\n\n[video.resolution]\n" + resolutionTable;
    }

    /** Runs configuration initialization alone with the production schema. */
    private Outcome initialize(LoggingMode mode) throws Exception {
        Optional<ApplicationSettings> settings;
        LoggingSystem logging = LoggingSystem.initialize(mode, harness.location(), FileOperations.SYSTEM,
                harness.stderr);
        try {
            StorageOperations configStorage = harness.storage;
            settings = new ConfigurationStartup(ApplicationSettings.SCHEMA, harness.configurationLocation,
                    (schema, dir) -> new TomlConfigurationPersistence(schema, dir, configStorage)).initialize();
        } finally {
            logging.close();
        }
        return new Outcome(settings, harness.records(), harness.log());
    }

    /** Runs the full application startup with the production schema and a fake window system. */
    private Outcome launch(LoggingMode mode, FakeWindowSystem windowSystem, int expectedExit) {
        int exit = harness.launch(mode, windowSystem);
        assertEquals(expectedExit, exit, harness.stderr());
        return new Outcome(Optional.empty(), harness.records(), harness.log());
    }

    private static FakeWindowSystem windowWith(MonitorInfo monitor) {
        FakeWindowSystem windowSystem = new FakeWindowSystem();
        windowSystem.monitor = monitor;
        return windowSystem;
    }

    private static CommentedConfig parse(Path file) throws IOException {
        TomlParser parser = TomlFormat.instance().createParser();
        parser.setTomlVersion(TomlVersion.v1_0);
        return parser.parse(Files.readString(file, StandardCharsets.UTF_8));
    }

    private void assertPersistedVideo(boolean fullscreen, Object width, Object height) throws IOException {
        CommentedConfig persisted = parse(harness.settingsFile());
        assertEquals(List.of("video"), List.copyOf(persisted.valueMap().keySet()));
        assertEquals(List.of("fullscreen", "resolution"),
                List.copyOf(persisted.<CommentedConfig>get("video").valueMap().keySet()));
        assertEquals(fullscreen, persisted.get("video.fullscreen"));
        assertEquals(List.of("width", "height"),
                List.copyOf(persisted.<CommentedConfig>get("video.resolution").valueMap().keySet()));
        assertPersistedDimension(width, persisted.get("video.resolution.width"));
        assertPersistedDimension(height, persisted.get("video.resolution.height"));
    }

    private static void assertPersistedDimension(Object expected, Object actual) {
        if (expected instanceof Integer number) {
            assertEquals(number.intValue(), ((Number) actual).intValue());
        } else {
            assertEquals(expected, actual);
        }
    }

    // --- Fullscreen -----------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void configuredFullscreenChoosesTheStartupMode(boolean fullscreen) throws Exception {
        prepare();
        writeConfig(videoToml(String.valueOf(fullscreen), "width = \"auto\"\nheight = \"auto\"\n"));
        FakeWindowSystem windowSystem = new FakeWindowSystem();

        launch(LoggingMode.DEFAULT, windowSystem, 0);

        assertEquals(fullscreen, windowSystem.createdSettings.fullscreen());
        assertEquals("My strategy", windowSystem.createdSettings.title());
        assertEquals(new Resolution(1920, 1080), windowSystem.createdResolution);
    }

    @ParameterizedTest
    @EnumSource(LoggingMode.class)
    void invalidFullscreenIsNormalizedToTrue(LoggingMode mode) throws Exception {
        prepare();
        writeConfig(videoToml("\"no\"", "width = 1600\nheight = 900\n"));

        Outcome outcome = initialize(mode);

        assertEquals(true, outcome.settings().orElseThrow().get(VideoSettings.FULLSCREEN));
        assertEquals(VideoResolution.of(1600, 900), outcome.settings().orElseThrow().get(VideoSettings.RESOLUTION));
        assertPersistedVideo(true, 1600, 900);
        List<String> warnings = outcome.withLevel("WARN ");
        assertEquals(1, warnings.size(), outcome.records().toString());
        assertTrue(warnings.get(0).contains(CONFIG_LOGGER), warnings.get(0));
        assertTrue(warnings.get(0).contains("'video.fullscreen'"), warnings.get(0));
        assertTrue(warnings.get(0).contains("incompatible type; expected boolean"), warnings.get(0));
    }

    // --- Resolution -----------------------------------------------------------------------

    @Test
    void explicitResolutionIsUsedInsteadOfTheMonitorResolution() throws Exception {
        prepare();
        writeConfig(videoToml("true", "width = 1600\nheight = 900\n"));
        FakeWindowSystem windowSystem = windowWith(MONITOR_2560);

        launch(LoggingMode.DEFAULT, windowSystem, 0);

        assertEquals(new Resolution(1600, 900), windowSystem.createdResolution);
        assertTrue(windowSystem.createdSettings.fullscreen());
    }

    @Test
    void explicitResolutionNotAdvertisedByTheMonitorRemainsConfigured() throws Exception {
        prepare();
        writeConfig(videoToml("false", "width = 1234\nheight = 567\n"));
        byte[] original = Files.readAllBytes(harness.settingsFile());
        FakeWindowSystem windowSystem = windowWith(MONITOR_2560);

        Outcome outcome = launch(LoggingMode.DEFAULT, windowSystem, 0);

        assertEquals(new Resolution(1234, 567), windowSystem.createdResolution);
        assertArrayEquals(original, Files.readAllBytes(harness.settingsFile()));
        assertTrue(outcome.withLevel("WARN ").isEmpty(), outcome.records().toString());
    }

    @ParameterizedTest
    @EnumSource(LoggingMode.class)
    void invalidResolutionsAreReplacedAsAWholeAndResolvedFromTheMonitor(LoggingMode mode) throws Exception {
        for (int i = 0; i < INVALID_RESOLUTIONS.size(); i++) {
            prepare(Files.createDirectories(temp.resolve("case-" + mode + "-" + i)));
            String resolution = INVALID_RESOLUTIONS.get(i);
            writeConfig(videoToml("false", resolution));

            Outcome outcome = initialize(mode);

            assertSame(VideoResolution.AUTOMATIC, outcome.settings().orElseThrow().get(VideoSettings.RESOLUTION),
                    resolution);
            assertPersistedVideo(false, "auto", "auto");
            List<String> warnings = outcome.withLevel("WARN ");
            assertEquals(1, warnings.size(), resolution + outcome.records());
            assertTrue(warnings.get(0).contains(CONFIG_LOGGER), warnings.get(0));
            assertTrue(warnings.get(0).contains("'video.resolution'"), warnings.get(0));
            assertFalse(warnings.get(0).contains("video.resolution."), "one logical setting: " + warnings.get(0));
            assertTrue(warnings.get(0).contains("incompatible type; expected resolution")
                    || warnings.get(0).contains("failed validation"), warnings.get(0));

            FakeWindowSystem windowSystem = windowWith(MONITOR_2560);
            Outcome started = launch(mode, windowSystem, 0);
            assertEquals(new Resolution(2560, 1440), windowSystem.createdResolution, resolution);
            assertFalse(windowSystem.createdSettings.fullscreen());
            assertTrue(started.withLevel("WARN ").isEmpty(), "the normalized configuration is valid");
        }
    }

    @Test
    void nonPositiveResolutionReportsFailedValidation() throws Exception {
        prepare();
        writeConfig(videoToml("true", "width = 0\nheight = 0\n"));

        Outcome outcome = initialize(LoggingMode.DEFAULT);

        assertTrue(outcome.containing("WARN ", "'video.resolution'").get(0).contains("failed validation"));
    }

    @Test
    void mixedResolutionReportsWhatTheValidRepresentationsAre() throws Exception {
        prepare();
        writeConfig(videoToml("true", "width = 1600\nheight = \"auto\"\n"));

        Outcome outcome = initialize(LoggingMode.DEFAULT);

        assertTrue(outcome.containing("WARN ", "'video.resolution'").get(0).contains(
                "incompatible type; expected resolution (width and height both auto or both integers)"),
                outcome.records().toString());
    }

    @ParameterizedTest
    @EnumSource(LoggingMode.class)
    void invalidResolutionWithUnavailableMonitorUses1280x720(LoggingMode mode) throws Exception {
        prepare();
        writeConfig(videoToml("true", "width = 1600\nheight = \"auto\"\n"));
        FakeWindowSystem windowSystem = windowWith(NO_MONITOR);

        Outcome outcome = launch(mode, windowSystem, 0);

        assertEquals(new Resolution(1280, 720), windowSystem.createdResolution);
        assertPersistedVideo(true, "auto", "auto");
        assertEquals(1, outcome.containing("WARN ", "'video.resolution'").size(), outcome.records().toString());
        assertEquals(1, outcome.containing("WARN ", FALLBACK_WARNING).size(), outcome.records().toString());
        assertEquals(2, outcome.withLevel("WARN ").size(), outcome.records().toString());
    }

    // --- Normalization and persistence ----------------------------------------------------

    @ParameterizedTest
    @EnumSource(LoggingMode.class)
    void missingVideoSettingsAreAddedAndPersisted(LoggingMode mode) throws Exception {
        prepare();
        writeConfig("# saved before video settings existed\n");

        Outcome outcome = initialize(mode);

        assertEquals(ApplicationSettings.defaults(ApplicationSettings.SCHEMA), outcome.settings().orElseThrow());
        assertPersistedVideo(true, "auto", "auto");
        assertEquals(1, storage.count("move"), "the complete normalized configuration is saved once");
        assertTrue(outcome.withLevel("WARN ").isEmpty(), outcome.records().toString());
        List<String> debug = outcome.withLevel("DEBUG");
        if (mode == LoggingMode.DEFAULT) {
            assertTrue(debug.isEmpty(), debug.toString());
        } else {
            assertEquals(1, outcome.containing("DEBUG", "'video.fullscreen' is missing").size(), debug.toString());
            assertEquals(1, outcome.containing("DEBUG", "'video.resolution' is missing").size(), debug.toString());
            assertTrue(debug.stream().allMatch(r -> r.contains(CONFIG_LOGGER)), debug.toString());
        }
    }

    @Test
    void partiallyMissingVideoSectionIsCompleted() throws Exception {
        prepare();
        writeConfig("[video]\nfullscreen = false\n");

        Outcome outcome = initialize(LoggingMode.DEVELOPMENT);

        assertEquals(false, outcome.settings().orElseThrow().get(VideoSettings.FULLSCREEN));
        assertPersistedVideo(false, "auto", "auto");
        assertEquals(1, outcome.containing("DEBUG", "'video.resolution' is missing").size());
        assertTrue(outcome.containing("DEBUG", "'video.fullscreen' is missing").isEmpty());
    }

    @ParameterizedTest
    @EnumSource(LoggingMode.class)
    void validVideoSettingsAreNotRewritten(LoggingMode mode) throws Exception {
        List<String> configurations = List.of(
                videoToml("true", "width = \"auto\"\nheight = \"auto\"\n"),
                videoToml("false", "width = 1600\nheight = 900\n"));
        List<VideoResolution> expected = List.of(VideoResolution.AUTOMATIC, VideoResolution.of(1600, 900));
        for (int i = 0; i < configurations.size(); i++) {
            prepare(Files.createDirectories(temp.resolve("valid-" + mode + "-" + i)));
            writeConfig(configurations.get(i));
            FileTime past = FileTime.fromMillis(1_000_000_000_000L);
            Files.setLastModifiedTime(harness.settingsFile(), past);
            byte[] original = Files.readAllBytes(harness.settingsFile());

            Outcome outcome = initialize(mode);

            ApplicationSettings settings = outcome.settings().orElseThrow();
            assertEquals(i == 0, settings.get(VideoSettings.FULLSCREEN));
            assertEquals(expected.get(i), settings.get(VideoSettings.RESOLUTION));
            assertArrayEquals(original, Files.readAllBytes(harness.settingsFile()));
            assertEquals(past, Files.getLastModifiedTime(harness.settingsFile()));
            assertEquals(0, storage.count("createTempFile") + storage.count("move"));
            assertTrue(outcome.withLevel("WARN ").isEmpty());
        }
    }

    @Test
    void automaticResolutionIsPersistedAndLoadedWithoutMonitorDimensions() throws Exception {
        prepare();
        FakeWindowSystem windowSystem = windowWith(MONITOR_2560);

        launch(LoggingMode.DEFAULT, windowSystem, 0);

        assertEquals(new Resolution(2560, 1440), windowSystem.createdResolution);
        assertPersistedVideo(true, "auto", "auto");
        Outcome reloaded = initialize(LoggingMode.DEFAULT);
        assertSame(VideoResolution.AUTOMATIC, reloaded.settings().orElseThrow().get(VideoSettings.RESOLUTION));
    }

    // --- Default behavior ------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(LoggingMode.class)
    void defaultVideoSettingsPreserveTheMonitorResolutionBehavior(LoggingMode mode) {
        prepare();
        FakeWindowSystem windowSystem = windowWith(MONITOR_2560);

        Outcome outcome = launch(mode, windowSystem, 0);

        assertTrue(windowSystem.createdSettings.fullscreen());
        assertEquals("My strategy", windowSystem.createdSettings.title());
        assertEquals(new Resolution(2560, 1440), windowSystem.createdResolution);
        assertTrue(outcome.withLevel("WARN ").isEmpty());
    }

    @ParameterizedTest
    @EnumSource(LoggingMode.class)
    void defaultVideoSettingsPreserveTheFallbackBehavior(LoggingMode mode) {
        prepare();
        FakeWindowSystem windowSystem = windowWith(NO_MONITOR);

        Outcome outcome = launch(mode, windowSystem, 0);

        assertTrue(windowSystem.createdSettings.fullscreen());
        assertEquals("My strategy", windowSystem.createdSettings.title());
        assertEquals(new Resolution(1280, 720), windowSystem.createdResolution);
        assertEquals(1, outcome.containing("WARN ", FALLBACK_WARNING).size());
    }

    // --- Diagnostics ----------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(LoggingMode.class)
    void videoDiagnosticsAreDevelopmentOnly(LoggingMode mode) throws Exception {
        record Case(String toml, MonitorInfo monitor, String selection, String resolved) {
        }
        List<Case> cases = List.of(
                new Case(videoToml("true", "width = \"auto\"\nheight = \"auto\"\n"), MONITOR_2560,
                        "fullscreen true, resolution selection automatic",
                        "2560×1440 (source: monitor video mode)"),
                new Case(videoToml("false", "width = \"auto\"\nheight = \"auto\"\n"), NO_MONITOR,
                        "fullscreen false, resolution selection automatic",
                        "1280×720 (source: automatic fallback)"),
                new Case(videoToml("false", "width = 1600\nheight = 900\n"), MONITOR_2560,
                        "fullscreen false, resolution selection explicit 1600×900",
                        "1600×900 (source: explicit configuration)"),
                new Case(videoToml("true", "width = 1600\nheight = 900\n"), NO_MONITOR,
                        "fullscreen true, resolution selection explicit 1600×900",
                        "1600×900 (source: explicit configuration)"));
        for (int i = 0; i < cases.size(); i++) {
            Case c = cases.get(i);
            prepare(Files.createDirectories(temp.resolve("diag-" + mode + "-" + i)));
            writeConfig(c.toml());

            Outcome outcome = launch(mode, windowWith(c.monitor()), 0);

            List<String> selection = outcome.containing("DEBUG", SELECTION_EVENT);
            List<String> resolved = outcome.containing("DEBUG", RESOLVED_EVENT);
            if (mode == LoggingMode.DEFAULT) {
                assertTrue(outcome.withLevel("DEBUG").isEmpty(), outcome.records().toString());
                assertFalse(outcome.log().contains(SELECTION_EVENT));
                assertFalse(outcome.log().contains(RESOLVED_EVENT));
                continue;
            }
            assertEquals(1, selection.size(), outcome.records().toString());
            assertEquals(1, resolved.size(), outcome.records().toString());
            assertTrue(selection.get(0).contains(WINDOW_LOGGER + SELECTION_EVENT + ": " + c.selection()),
                    selection.get(0));
            assertTrue(resolved.get(0).contains(WINDOW_LOGGER + RESOLVED_EVENT + ": " + c.resolved()),
                    resolved.get(0));
            String log = outcome.log();
            assertTrue(log.indexOf("Configuration initialized") < log.indexOf(SELECTION_EVENT));
            assertTrue(log.indexOf(SELECTION_EVENT) < log.indexOf(RESOLVED_EVENT));
            assertTrue(log.indexOf(RESOLVED_EVENT) < log.indexOf("Window opened displaying black"));
        }
    }

    @Test
    void normalStartupUsesTheDefaultLoggingModeWithoutAModeSetting() {
        ApplicationLauncher normal = ApplicationLauncher.forNormalStartup();

        assertEquals(LoggingMode.DEFAULT, normal.mode());
        assertEquals(List.of("video.fullscreen", "video.resolution"),
                normal.settingsSchema().settings().stream().map(s -> s.id()).toList());
    }

    @ParameterizedTest
    @EnumSource(LoggingMode.class)
    void normalizationDiagnosticsAreNotDuplicatedByVideoHandling(LoggingMode mode) throws Exception {
        List<String> configurations = new ArrayList<>();
        configurations.add(videoToml("42", "width = \"auto\"\nheight = \"auto\"\n"));
        INVALID_RESOLUTIONS.forEach(resolution -> configurations.add(videoToml("true", resolution)));
        configurations.add("[video]\n");
        for (int i = 0; i < configurations.size(); i++) {
            prepare(Files.createDirectories(temp.resolve("reuse-" + mode + "-" + i)));
            writeConfig(configurations.get(i));

            Outcome outcome = launch(mode, windowWith(MONITOR_2560), 0);

            List<String> warnings = outcome.withLevel("WARN ");
            boolean missing = i == configurations.size() - 1;
            assertEquals(missing ? 0 : 1, warnings.size(), outcome.records().toString());
            assertTrue(warnings.stream().allMatch(w -> w.contains(CONFIG_LOGGER)), warnings.toString());
            if (i == 0) {
                assertEquals(1, count(outcome.log(), "'video.fullscreen'"), outcome.log());
            } else if (!missing) {
                assertEquals(1, count(outcome.log(), "'video.resolution'"), outcome.log());
            }
            if (missing && mode == LoggingMode.DEVELOPMENT) {
                assertEquals(1, outcome.containing("DEBUG", "'video.fullscreen' is missing").size());
                assertEquals(1, outcome.containing("DEBUG", "'video.resolution' is missing").size());
            }
            assertTrue(outcome.records().stream().filter(r -> r.contains(WINDOW_LOGGER))
                    .noneMatch(r -> r.contains("'video.")), "the window does not repeat configuration events");
        }
    }

    @ParameterizedTest
    @EnumSource(LoggingMode.class)
    void fallbackWarningOnlyForAutomaticSelection(LoggingMode mode) throws Exception {
        prepare(Files.createDirectories(temp.resolve("auto")));
        writeConfig(videoToml("true", "width = \"auto\"\nheight = \"auto\"\n"));
        FakeWindowSystem automatic = windowWith(NO_MONITOR);
        Outcome automaticOutcome = launch(mode, automatic, 0);

        assertEquals(new Resolution(1280, 720), automatic.createdResolution);
        assertEquals(1, automaticOutcome.containing("WARN ", FALLBACK_WARNING).size());
        assertTrue(automaticOutcome.containing("WARN ", FALLBACK_WARNING).get(0).contains("simulated no video mode"));

        prepare(Files.createDirectories(temp.resolve("explicit")));
        writeConfig(videoToml("true", "width = 1600\nheight = 900\n"));
        FakeWindowSystem explicit = windowWith(NO_MONITOR);
        Outcome explicitOutcome = launch(mode, explicit, 0);

        assertEquals(new Resolution(1600, 900), explicit.createdResolution);
        assertTrue(explicitOutcome.withLevel("WARN ").isEmpty(), explicitOutcome.records().toString());
        if (mode == LoggingMode.DEVELOPMENT) {
            assertEquals(1, automaticOutcome.containing("DEBUG", "source: automatic fallback").size());
            assertEquals(1, explicitOutcome.containing("DEBUG", "source: explicit configuration").size());
        }
    }

    @ParameterizedTest
    @EnumSource(LoggingMode.class)
    void successfulWindowEventReportsTheActualState(LoggingMode mode) throws Exception {
        record Case(String fullscreen, String resolution, WindowState actual) {
        }
        List<Case> cases = List.of(
                new Case("true", "width = \"auto\"\nheight = \"auto\"\n", null),
                new Case("false", "width = \"auto\"\nheight = \"auto\"\n", null),
                new Case("true", "width = 1600\nheight = 900\n", null),
                new Case("false", "width = 1600\nheight = 900\n", null),
                new Case("true", "width = 1600\nheight = 900\n", new WindowState(new Resolution(1680, 1050), true)),
                new Case("false", "width = 1600\nheight = 900\n", new WindowState(new Resolution(1600, 877), false)),
                new Case("true", "width = \"auto\"\nheight = \"auto\"\n",
                        new WindowState(new Resolution(1920, 1080), false)));
        for (int i = 0; i < cases.size(); i++) {
            Case c = cases.get(i);
            prepare(Files.createDirectories(temp.resolve("info-" + mode + "-" + i)));
            writeConfig(videoToml(c.fullscreen(), c.resolution()));
            FakeWindowSystem windowSystem = windowWith(MONITOR_2560);
            windowSystem.actualState = c.actual();

            Outcome outcome = launch(mode, windowSystem, 0);

            WindowState actual = c.actual() != null ? c.actual()
                    : new WindowState(windowSystem.createdResolution, windowSystem.createdSettings.fullscreen());
            List<String> opened = outcome.containing("INFO ", "Window opened displaying black");
            assertEquals(1, opened.size(), outcome.records().toString());
            assertTrue(opened.get(0).contains("title 'My strategy', resolution " + actual.resolution()
                    + ", fullscreen " + actual.fullscreen()), opened.get(0));
            if (c.actual() != null) {
                assertFalse(opened.get(0).contains("resolution " + windowSystem.createdResolution + ","),
                        "requested values are not reported as actual: " + opened.get(0));
            }
        }
    }

    @ParameterizedTest
    @EnumSource(LoggingMode.class)
    void platformRejectionOfValidExplicitDimensionsIsFatalWithoutRetry(LoggingMode mode) throws Exception {
        prepare();
        writeConfig(videoToml("true", "width = 7680\nheight = 4320\n"));
        byte[] original = Files.readAllBytes(harness.settingsFile());
        FakeWindowSystem windowSystem = windowWith(MONITOR_2560);
        windowSystem.createWindowFailure = new IllegalStateException("glfwCreateWindow returned NULL");

        Outcome outcome = launch(mode, windowSystem, 1);

        assertEquals(1, windowSystem.events.stream().filter("createWindow"::equals).count(), "no retry");
        assertArrayEquals(original, Files.readAllBytes(harness.settingsFile()), "no normalization rewrite");
        assertEquals(0, storage.count("move"));
        List<String> errors = outcome.withLevel("ERROR");
        assertEquals(1, errors.size(), outcome.records().toString());
        assertTrue(errors.get(0).contains("Window creation failed"), errors.get(0));
        assertTrue(outcome.withLevel("WARN ").isEmpty(), outcome.records().toString());
        assertFalse(outcome.log().contains("Normal shutdown completed"));
        assertFalse(outcome.log().contains("Window opened"));
        assertTrue(windowSystem.events.contains("terminate"), "inherited cleanup runs");
        try (FileChannel channel = FileChannel.open(harness.logFile(), StandardOpenOption.WRITE)) {
            FileLock lock = channel.tryLock(FileOperations.OWNERSHIP_LOCK_POSITION, 1, false);
            assertNotNull(lock, "log ownership was released");
            lock.release();
        }
    }

    // --- Startup order, logging ownership and isolation -----------------------------------

    @Test
    void loggingPrecedesConfigurationWhichPrecedesVideoStartup() throws Exception {
        List<String> events = Collections.synchronizedList(new ArrayList<>());
        prepare();
        harness.storage = new RecordingStorage(events);
        writeConfig(videoToml("false", "width = 1600\nheight = 900\n"));
        FileOperations recording = new FileOperations() {
            @Override
            public FileChannel open(Path file) throws IOException {
                events.add("log-open");
                return FileOperations.super.open(file);
            }

            @Override
            public void truncate(FileChannel channel) throws IOException {
                events.add("log-truncate");
                FileOperations.super.truncate(channel);
            }

            @Override
            public void write(FileChannel channel, ByteBuffer data) throws IOException {
                events.add("log-write " + StandardCharsets.UTF_8.decode(data.duplicate()).toString().strip());
                FileOperations.super.write(channel, data);
            }
        };
        FakeWindowSystem windowSystem = new FakeWindowSystem(events);

        int exit = harness.launch(LoggingMode.DEVELOPMENT, recording, harness.location(), windowSystem);

        assertEquals(0, exit, harness.stderr());
        assertEquals(1, events.stream().filter("log-open"::equals).count(), "log.log is opened once");
        assertEquals(1, events.stream().filter("log-truncate"::equals).count(), "log.log is not truncated again");
        int truncate = events.indexOf("log-truncate");
        int configRead = indexOf(events, "config-read");
        int initialized = indexOf(events, "log-write", "Configuration initialized");
        int selection = indexOf(events, "log-write", SELECTION_EVENT);
        int glfw = events.indexOf("initialize");
        int created = events.indexOf("createWindow");
        assertTrue(truncate < configRead && configRead < initialized, events.toString());
        assertTrue(initialized < selection && selection < glfw && glfw < created, events.toString());
        assertEquals(new Resolution(1600, 900), windowSystem.createdResolution);
        assertTrue(harness.log().contains("Application startup begins"), "earlier records are kept");
    }

    private static int indexOf(List<String> events, String prefix, String... texts) {
        for (int i = 0; i < events.size(); i++) {
            String event = events.get(i);
            if (event.startsWith(prefix) && Arrays.stream(texts).allMatch(event::contains)) {
                return i;
            }
        }
        return -1;
    }

    @Test
    void loggingFailurePreventsConfigurationAndVideoStartup() throws Exception {
        prepare();
        writeConfig(videoToml("false", "width = 1600\nheight = 900\n"));
        FakeWindowSystem windowSystem = new FakeWindowSystem();

        int exit = harness.launch(LoggingMode.DEFAULT, FileOperations.SYSTEM, () -> {
            throw new IllegalStateException("simulated logging location failure");
        }, windowSystem);

        assertEquals(1, exit);
        assertTrue(storage.events.isEmpty(), storage.events.toString());
        assertFalse(harness.infrastructureStarted.get());
        assertTrue(windowSystem.events.isEmpty());
    }

    @Test
    void configurationFailurePreventsVideoStartup() throws Exception {
        prepare();
        writeConfig("[video\nfullscreen = false\n");
        FakeWindowSystem windowSystem = new FakeWindowSystem();

        Outcome outcome = launch(LoggingMode.DEVELOPMENT, windowSystem, 1);

        assertFalse(harness.infrastructureStarted.get());
        assertTrue(windowSystem.events.isEmpty());
        assertFalse(outcome.log().contains(SELECTION_EVENT));
        assertEquals(1, outcome.withLevel("ERROR").size());
    }

    @Test
    void videoNormalizationPreservesTheLogAndIsolatesFiles() throws Exception {
        prepare();
        writeConfig(videoToml("\"SECRET\"", "width = 1600\nheight = \"auto\"\n"));
        LoggingSystem logging = LoggingSystem.initialize(LoggingMode.DEFAULT, harness.location(),
                FileOperations.SYSTEM, harness.stderr);
        try {
            String marker = "VIDEO-SESSION-MARKER-" + temp.getFileName();
            LoggerFactory.getLogger("test").info(marker);
            byte[] logBefore = Files.readAllBytes(harness.logFile());

            ApplicationSettings normalized = new ConfigurationStartup(ApplicationSettings.SCHEMA,
                    harness.configurationLocation,
                    (schema, dir) -> new TomlConfigurationPersistence(schema, dir, storage)).initialize().orElseThrow();
            assertEquals(ApplicationSettings.defaults(ApplicationSettings.SCHEMA), normalized);
            assertPersistedVideo(true, "auto", "auto");
            assertLogPreserved(logBefore, marker);

            // A failed save of a normalization preserves the existing configuration and the log.
            writeConfig("[video]\n");
            byte[] existing = Files.readAllBytes(harness.settingsFile());
            RecordingStorage failing = new RecordingStorage();
            failing.moveFailure = new IOException("simulated replacement failure");
            assertTrue(new ConfigurationStartup(ApplicationSettings.SCHEMA, harness.configurationLocation,
                    (schema, dir) -> new TomlConfigurationPersistence(schema, dir, failing)).initialize().isEmpty());
            assertArrayEquals(existing, Files.readAllBytes(harness.settingsFile()));
            assertLogPreserved(logBefore, marker);

            List<Path> accessed = new ArrayList<>(storage.paths);
            accessed.addAll(failing.paths);
            assertFalse(accessed.isEmpty());
            for (Path path : accessed) {
                assertTrue(path.toAbsolutePath().startsWith(temp), "outside the isolated location: " + path);
            }
        } finally {
            logging.close();
        }
    }

    private void assertLogPreserved(byte[] before, String marker) throws IOException {
        byte[] now = Files.readAllBytes(harness.logFile());
        assertTrue(now.length >= before.length, "log was truncated");
        assertArrayEquals(before, Arrays.copyOf(now, before.length), "existing records were replaced");
        assertTrue(harness.log().contains(marker));
        try (FileChannel channel = FileChannel.open(harness.logFile(), StandardOpenOption.WRITE)) {
            assertThrows(OverlappingFileLockException.class,
                    () -> channel.tryLock(FileOperations.OWNERSHIP_LOCK_POSITION, 1, false),
                    "exclusive log ownership remains active");
        }
    }

    @Test
    void videoDiagnosticsNeverContainRawValues() throws Exception {
        List<String> configurations = List.of(
                "[video]\nfullscreen = \"SECRET-FULLSCREEN-MARKER\"\n\n[video.resolution]\n"
                        + "width = \"SECRET-WIDTH-MARKER\"\nheight = 900\n\n[unrelated]\nkey = \"SECRET-UNRELATED\"\n",
                "[video]\nfullscreen = true\n\n[video.resolution]\nwidth = 1600\nheight = \"SECRET-HEIGHT\"\n"
                        + "depth = \"SECRET-EXTRA\"\n",
                "[video]\nfullscreen = false\n\n[video.resolution]\nwidth = 987654\nheight = -123456\n");
        for (int i = 0; i < configurations.size(); i++) {
            prepare(Files.createDirectories(temp.resolve("secret-" + i)));
            writeConfig(configurations.get(i));

            Outcome outcome = launch(LoggingMode.DEVELOPMENT, windowWith(MONITOR_2560), 0);

            String log = outcome.log();
            assertFalse(log.contains("SECRET"), log);
            assertFalse(harness.stderr().contains("SECRET"), harness.stderr());
            assertFalse(log.contains("987654") || log.contains("123456"), "invalid dimensions are not logged: " + log);
            assertFalse(log.contains("[video"), "no TOML contents are logged");
            assertFalse(log.contains("width ="), "no TOML contents are logged");
            assertFalse(log.contains("ApplicationSettings["), "no snapshots are logged");
            assertTrue(log.contains("resolution selection automatic"), "operational choices may appear");
            assertTrue(log.contains("2560×1440"), "effective dimensions may appear");
        }
    }
}
