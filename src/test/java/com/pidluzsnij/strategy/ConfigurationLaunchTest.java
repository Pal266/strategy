package com.pidluzsnij.strategy;

import ch.qos.logback.classic.LoggerContext;
import com.pidluzsnij.strategy.config.ApplicationSettings;
import com.pidluzsnij.strategy.config.VideoSettings;
import com.pidluzsnij.strategy.config.persistence.ConfigurationPersistenceException;
import com.pidluzsnij.strategy.config.persistence.LoadResult;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import static com.pidluzsnij.strategy.testsupport.LogHarness.count;
import static com.pidluzsnij.strategy.testsupport.TestSettings.COUNT;
import static com.pidluzsnij.strategy.testsupport.TestSettings.SCHEMA;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Configuration within the application startup and shutdown flow. */
class ConfigurationLaunchTest {

    @TempDir
    Path temp;

    private final List<String> events = Collections.synchronizedList(new ArrayList<>());

    /** Logging file operations that record into {@link #events}. */
    private FileOperations recordingLogOperations(Consumer<String> onWrite) {
        return new FileOperations() {
            @Override
            public FileChannel open(Path file) throws IOException {
                events.add("log-open " + file);
                return FileOperations.super.open(file);
            }

            @Override
            public void createDirectories(Path directory) throws IOException {
                events.add("log-createDirectories " + directory);
                FileOperations.super.createDirectories(directory);
            }

            @Override
            public void write(FileChannel channel, ByteBuffer data) throws IOException {
                String text = StandardCharsets.UTF_8.decode(data.duplicate()).toString();
                onWrite.accept(text);
                FileOperations.super.write(channel, data);
                events.add("log-write " + text.strip());
            }

            @Override
            public void release(FileLock lock) throws IOException {
                events.add("log-release");
                FileOperations.super.release(lock);
            }

            @Override
            public void close(FileChannel channel) throws IOException {
                events.add("log-close");
                FileOperations.super.close(channel);
            }
        };
    }

    private int indexOf(String prefix) {
        for (int i = 0; i < events.size(); i++) {
            if (events.get(i).startsWith(prefix)) {
                return i;
            }
        }
        return -1;
    }

    private static void assertLogOwnershipReleased(Path logFile) throws IOException {
        try (FileChannel channel = FileChannel.open(logFile, StandardOpenOption.WRITE)) {
            FileLock lock = channel.tryLock(FileOperations.OWNERSHIP_LOCK_POSITION, 1, false);
            assertNotNull(lock, "log ownership was released");
            lock.release();
        }
    }

    // --- Startup order --------------------------------------------------------------------

    @Test
    void loggingThenConfigurationThenWindow() {
        LogHarness harness = new LogHarness(temp);
        harness.storage = new RecordingStorage(events);
        FakeWindowSystem windowSystem = new FakeWindowSystem(events);

        int exit = harness.launch(LoggingMode.DEFAULT, recordingLogOperations(text -> { }), harness.location(),
                windowSystem);

        assertEquals(0, exit, harness.stderr());
        int logOpen = indexOf("log-open");
        int configRead = indexOf("config-read");
        int configSaved = indexOf("config-move");
        int initialized = indexOf("log-write") >= 0 ? eventsIndexContaining("Configuration initialized") : -1;
        int glfw = events.indexOf("initialize");
        assertTrue(logOpen >= 0 && logOpen < configRead, events.toString());
        assertTrue(configRead < configSaved && configSaved < initialized, events.toString());
        assertTrue(initialized < glfw, events.toString());
    }

    private int eventsIndexContaining(String text) {
        for (int i = 0; i < events.size(); i++) {
            if (events.get(i).contains(text)) {
                return i;
            }
        }
        return -1;
    }

    @Test
    void loggingFailurePreventsConfigurationAndWindow() {
        LogHarness harness = new LogHarness(temp);
        RecordingStorage storage = new RecordingStorage(events);
        harness.storage = storage;
        FakeWindowSystem windowSystem = new FakeWindowSystem(events);

        int exit = harness.launch(LoggingMode.DEFAULT, FileOperations.SYSTEM, () -> {
            throw new IllegalStateException("simulated logging location failure");
        }, windowSystem);

        assertEquals(1, exit);
        assertTrue(harness.stderr().contains("diagnostic logging could not be initialized"), harness.stderr());
        assertTrue(events.isEmpty(), "no configuration or window initialization: " + events);
        assertFalse(harness.infrastructureStarted.get());
        assertFalse(Files.exists(harness.settingsFile()));
    }

    // --- Fatal failure cleanup ------------------------------------------------------------

    enum Failure { LOCATION, LOAD, MALFORMED, DIRECTORY_CREATION, SAVE }

    @ParameterizedTest
    @EnumSource(Failure.class)
    void fatalConfigurationFailureClosesLoggingWithoutNormalTermination(Failure failure) throws Exception {
        LogHarness harness = new LogHarness(temp);
        RecordingStorage storage = new RecordingStorage(events);
        harness.storage = storage;
        harness.settingsSchema = SCHEMA;
        String operation;
        switch (failure) {
            case LOCATION -> {
                harness.configurationLocation = () -> {
                    throw new IllegalStateException("simulated location failure");
                };
                operation = "resolve configuration location";
            }
            case LOAD -> {
                Files.createDirectories(harness.settingsFile());
                operation = "read configuration file";
            }
            case MALFORMED -> {
                Files.createDirectories(harness.settingsFile().getParent());
                Files.writeString(harness.settingsFile(), "= broken");
                operation = "parse configuration file";
            }
            case DIRECTORY_CREATION -> {
                // A separately supplied configuration location whose strategy directory cannot be created.
                Path separate = temp.resolve("separate-config");
                harness.configurationLocation = () -> separate;
                storage.createDirectoriesFailure = new java.nio.file.AccessDeniedException(separate.toString());
                operation = "create configuration directory";
            }
            case SAVE -> {
                storage.moveFailure = new IOException("simulated replacement failure");
                operation = "replace configuration file";
            }
            default -> throw new AssertionError(failure);
        }
        FakeWindowSystem windowSystem = new FakeWindowSystem(events);

        int exit = harness.launch(LoggingMode.DEFAULT, recordingLogOperations(text -> { }), harness.location(),
                windowSystem);

        assertEquals(1, exit, harness.stderr());
        assertFalse(harness.infrastructureStarted.get(), "window startup does not occur");
        assertFalse(events.contains("initialize"));
        String log = harness.log();
        List<String> errors = harness.records().stream().filter(r -> r.contains(" ERROR [")).toList();
        assertEquals(1, errors.size(), log);
        assertTrue(errors.get(0).contains(operation), errors.get(0));
        assertTrue(errors.get(0).contains("\tat "), errors.get(0));
        assertEquals(1, count(log, "Configuration initialization failed"));
        assertFalse(log.contains("Normal shutdown completed"));
        int error = eventsIndexContaining("Configuration initialization failed");
        int release = events.indexOf("log-release");
        int close = events.indexOf("log-close");
        assertTrue(error >= 0 && error < release && release < close, events.toString());
        assertEquals(close, events.size() - 1, "nothing is written after logging closes");
        assertLogOwnershipReleased(harness.logFile());
    }

    @Test
    void logWriteFailureFallsBackToStderrAndStillCleansUp() throws Exception {
        LogHarness harness = new LogHarness(temp);
        Files.createDirectories(harness.settingsFile().getParent());
        Files.writeString(harness.settingsFile(), "= broken");
        FakeWindowSystem windowSystem = new FakeWindowSystem(events);
        FileOperations failingWrites = recordingLogOperations(text -> {
            if (text.contains("Configuration initialization failed")) {
                throw new IllegalStateException("simulated log write failure");
            }
        });
        FileOperations operations = new FileOperations() {
            @Override
            public FileChannel open(Path file) throws IOException {
                return failingWrites.open(file);
            }

            @Override
            public void write(FileChannel channel, ByteBuffer data) throws IOException {
                try {
                    failingWrites.write(channel, data);
                } catch (IllegalStateException e) {
                    throw new IOException(e.getMessage());
                }
            }

            @Override
            public void release(FileLock lock) throws IOException {
                failingWrites.release(lock);
            }

            @Override
            public void close(FileChannel channel) throws IOException {
                failingWrites.close(channel);
            }
        };

        int exit = harness.launch(LoggingMode.DEFAULT, operations, harness.location(), windowSystem);

        assertEquals(1, exit);
        assertTrue(harness.stderr().contains("Failed to write diagnostic log record"), harness.stderr());
        assertTrue(harness.stderr().contains("simulated log write failure"), harness.stderr());
        assertFalse(harness.infrastructureStarted.get());
        assertFalse(harness.log().contains("Configuration initialization failed"));
        assertTrue(events.contains("log-release"), events.toString());
        assertTrue(events.contains("log-close"), events.toString());
        assertLogOwnershipReleased(harness.logFile());
    }

    // --- Shared directory preservation ----------------------------------------------------

    @Test
    void logAndConfigurationPreserveEachOtherInTheSharedDirectory() throws Exception {
        LogHarness harness = new LogHarness(temp);
        Files.createDirectories(harness.settingsFile().getParent());
        Files.writeString(harness.settingsFile(), TestSettings.COMPLETE_VALID_TOML);
        byte[] configuration = Files.readAllBytes(harness.settingsFile());

        LoggingSystem logging = LoggingSystem.initialize(LoggingMode.DEFAULT, harness.location(),
                FileOperations.SYSTEM, harness.stderr);
        try {
            assertArrayEquals(configuration, Files.readAllBytes(harness.settingsFile()),
                    "log initialization preserves the configuration");
            LoggerFactory.getLogger("test").info("SESSION-MARKER-{}", temp.getFileName());
            String marker = "SESSION-MARKER-" + temp.getFileName();
            byte[] logBefore = Files.readAllBytes(harness.logFile());

            TomlConfigurationPersistence persistence = new TomlConfigurationPersistence(SCHEMA, harness.configDirectory);
            assertInstanceOf(LoadResult.Loaded.class, persistence.load());
            assertLogPreserved(harness, logBefore, marker);

            ApplicationSettings replacement = ApplicationSettings.defaults(SCHEMA).with(COUNT, 9);
            persistence.save(replacement);
            assertLogPreserved(harness, logBefore, marker);
            byte[] savedConfiguration = Files.readAllBytes(harness.settingsFile());

            RecordingStorage failing = new RecordingStorage();
            failing.moveFailure = new IOException("simulated replacement failure");
            assertThrows(ConfigurationPersistenceException.class,
                    () -> new TomlConfigurationPersistence(SCHEMA, harness.configDirectory, failing)
                            .save(ApplicationSettings.defaults(SCHEMA)));
            assertLogPreserved(harness, logBefore, marker);
            assertArrayEquals(savedConfiguration, Files.readAllBytes(harness.settingsFile()),
                    "failed save preserves the valid configuration");
            assertEquals(replacement, ((LoadResult.Loaded) persistence.load()).settings());
        } finally {
            logging.close();
        }
        assertLogOwnershipReleased(harness.logFile());
    }

    private static void assertLogPreserved(LogHarness harness, byte[] before, String marker) throws IOException {
        byte[] now = Files.readAllBytes(harness.logFile());
        assertTrue(now.length >= before.length, "log was truncated");
        assertArrayEquals(before, java.util.Arrays.copyOf(now, before.length), "existing records were replaced");
        assertTrue(harness.log().contains(marker));
        try (FileChannel channel = FileChannel.open(harness.logFile(), StandardOpenOption.WRITE)) {
            assertThrows(OverlappingFileLockException.class,
                    () -> channel.tryLock(FileOperations.OWNERSHIP_LOCK_POSITION, 1, false),
                    "exclusive log ownership remains active");
        }
    }

    // --- Test isolation -------------------------------------------------------------------

    @Test
    void startupFileAccessStaysWithinSuppliedLocations() throws Exception {
        for (boolean fatal : List.of(false, true)) {
            Path root = Files.createDirectories(temp.resolve(fatal ? "fatal" : "success"));
            LogHarness harness = new LogHarness(root);
            RecordingStorage storage = new RecordingStorage(events);
            harness.storage = storage;
            if (fatal) {
                Files.createDirectories(harness.settingsFile().getParent());
                Files.writeString(harness.settingsFile(), "= broken");
            }
            events.clear();

            int exit = harness.launch(LoggingMode.DEFAULT, recordingLogOperations(text -> { }), harness.location(),
                    new FakeWindowSystem());

            assertEquals(fatal ? 1 : 0, exit, harness.stderr());
            List<Path> accessed = new ArrayList<>(storage.paths);
            events.stream().filter(e -> e.startsWith("log-open ") || e.startsWith("log-createDirectories "))
                    .map(e -> Path.of(e.substring(e.indexOf(' ') + 1)))
                    .forEach(accessed::add);
            assertFalse(accessed.isEmpty());
            for (Path path : accessed) {
                assertTrue(path.toAbsolutePath().startsWith(root), "outside the supplied locations: " + path);
            }
        }
    }

    // --- Logging mode ---------------------------------------------------------------------

    @Test
    void normalStartupHasNoConfigurableLoggingMode() {
        ApplicationLauncher normal = ApplicationLauncher.forNormalStartup();

        assertEquals(LoggingMode.DEFAULT, normal.mode());
        assertEquals(ApplicationSettings.SCHEMA, normal.settingsSchema());
        assertEquals(List.of(VideoSettings.FULLSCREEN, VideoSettings.RESOLUTION,
                        com.pidluzsnij.strategy.config.LocalizationSettings.LANGUAGE),
                normal.settingsSchema().settings(), "no logging-mode setting; only the video and language settings");
    }

    @ParameterizedTest
    @EnumSource(LoggingMode.class)
    void configurationDoesNotChangeTheActiveLoggingMode(LoggingMode mode) throws Exception {
        LogHarness harness = new LogHarness(temp);
        LoggingSystem logging = LoggingSystem.initialize(mode, harness.location(), FileOperations.SYSTEM,
                harness.stderr);
        try {
            LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
            var before = context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).getLevel();

            new ConfigurationStartup(ApplicationSettings.SCHEMA, () -> harness.configDirectory,
                    TomlConfigurationPersistence::new).initialize().orElseThrow();

            assertEquals(before, context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).getLevel());
            assertEquals(mode == LoggingMode.DEVELOPMENT, LoggerFactory.getLogger("probe").isDebugEnabled());
        } finally {
            logging.close();
        }
    }
}
