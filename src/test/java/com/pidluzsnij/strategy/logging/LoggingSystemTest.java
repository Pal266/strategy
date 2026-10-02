package com.pidluzsnij.strategy.logging;

import com.pidluzsnij.strategy.logging.LoggingInitializationException.Reason;
import com.pidluzsnij.strategy.testsupport.FakeWindowSystem;
import com.pidluzsnij.strategy.testsupport.LogHarness;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static com.pidluzsnij.strategy.testsupport.LogHarness.count;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoggingSystemTest {

    private static final String PREVIOUS_SESSION = "previous-session-marker-7f3a";

    @TempDir
    Path temp;

    // --- Previous log replacement ---------------------------------------------------------

    @Test
    void previousSessionRecordsAreReplacedWithoutRotation() throws Exception {
        LogHarness harness = new LogHarness(temp);
        Files.createDirectories(harness.logFile().getParent());
        Files.writeString(harness.logFile(), PREVIOUS_SESSION + System.lineSeparator());

        LoggingSystem logging = LoggingSystem.initialize(LoggingMode.DEFAULT, harness.location(),
                FileOperations.SYSTEM, harness.stderr);
        try {
            LoggerFactory.getLogger("test").info("Application startup begins (logging mode: default)");
        } finally {
            logging.close();
        }

        String log = harness.log();
        assertFalse(log.contains(PREVIOUS_SESSION));
        assertTrue(log.contains("Application startup begins"));
        try (Stream<Path> files = Files.list(harness.logFile().getParent())) {
            assertEquals(List.of(harness.logFile()), files.toList(), "no rotation, archive or other log files");
        }
    }

    // --- Initialization failures ----------------------------------------------------------

    @Test
    void locationResolutionFailureAbortsStartup() {
        LogHarness harness = new LogHarness(temp);
        FakeWindowSystem windowSystem = new FakeWindowSystem();

        int exit = harness.launch(LoggingMode.DEFAULT, FileOperations.SYSTEM, () -> {
            throw new IllegalStateException("no home directory");
        }, windowSystem);

        assertEquals(1, exit);
        assertTrue(harness.stderr().contains("per-user configuration directory"), harness.stderr());
        assertTrue(harness.stderr().contains("no home directory"), harness.stderr());
        assertFalse(harness.infrastructureStarted.get());
        assertTrue(windowSystem.events.isEmpty());
    }

    @Test
    void directoryCreationFailureAbortsStartup() {
        assertInitializationFailure(new FileOperations() {
            @Override
            public void createDirectories(Path directory) throws IOException {
                throw new IOException("simulated directory failure");
            }
        }, "simulated directory failure", Reason.DIRECTORY_CREATION_FAILED, false);
    }

    @Test
    void fileOpeningFailureAbortsStartup() {
        assertInitializationFailure(new FileOperations() {
            @Override
            public FileChannel open(Path file) throws IOException {
                throw new IOException("simulated open failure");
            }
        }, "simulated open failure", Reason.FILE_OPEN_FAILED, false);
    }

    @Test
    void ownershipAcquisitionFailureAbortsStartupAndLeavesFileUnchanged() {
        assertInitializationFailure(new FileOperations() {
            @Override
            public FileLock tryLock(FileChannel channel) throws IOException {
                throw new IOException("simulated lock failure");
            }
        }, "simulated lock failure", Reason.OWNERSHIP_ACQUISITION_FAILED, true);
    }

    @Test
    void truncationFailureAbortsStartupAndLeavesFileUnchanged() {
        assertInitializationFailure(new FileOperations() {
            @Override
            public void truncate(FileChannel channel) throws IOException {
                throw new IOException("simulated truncation failure");
            }
        }, "simulated truncation failure", Reason.TRUNCATION_FAILED, true);
    }

    @Test
    void fileInUseIsDistinguishedAndLeavesFileUnchanged() throws Exception {
        LogHarness harness = new LogHarness(temp);
        Files.createDirectories(harness.logFile().getParent());
        Files.writeString(harness.logFile(), PREVIOUS_SESSION);
        FakeWindowSystem windowSystem = new FakeWindowSystem();

        try (FileChannel owner = FileOperations.SYSTEM.open(harness.logFile());
             FileLock ignored = FileOperations.SYSTEM.tryLock(owner)) {
            LoggingInitializationException failure = assertThrows(LoggingInitializationException.class,
                    () -> LoggingSystem.initialize(LoggingMode.DEFAULT, harness.location(), FileOperations.SYSTEM,
                            harness.stderr));
            assertEquals(Reason.FILE_IN_USE, failure.reason());

            int exit = harness.launch(LoggingMode.DEFAULT, windowSystem);
            assertEquals(1, exit);
        }

        String stderr = harness.stderr();
        assertTrue(stderr.contains("already in use"), stderr);
        assertTrue(stderr.contains(harness.logFile().toAbsolutePath().toString()), stderr);
        assertFalse(harness.infrastructureStarted.get());
        assertEquals(PREVIOUS_SESSION, Files.readString(harness.logFile()));
    }

    private void assertInitializationFailure(FileOperations failing, String detail, Reason reason,
                                             boolean withExistingFile) {
        try {
            LogHarness harness = new LogHarness(temp);
            if (withExistingFile) {
                Files.createDirectories(harness.logFile().getParent());
                Files.writeString(harness.logFile(), PREVIOUS_SESSION);
            }
            LoggingInitializationException failure = assertThrows(LoggingInitializationException.class,
                    () -> LoggingSystem.initialize(LoggingMode.DEFAULT, harness.location(), failing, harness.stderr));
            assertEquals(reason, failure.reason());
            assertEquals(harness.logFile().toAbsolutePath(), failure.attemptedPath());

            FakeWindowSystem windowSystem = new FakeWindowSystem();
            int exit = harness.launch(LoggingMode.DEFAULT, failing, harness.location(), windowSystem);

            String stderr = harness.stderr();
            assertEquals(1, exit);
            assertTrue(stderr.contains(detail), stderr);
            assertTrue(stderr.contains(harness.logFile().toAbsolutePath().toString()), stderr);
            assertFalse(stderr.contains("already in use"), stderr);
            assertFalse(harness.infrastructureStarted.get(), "no other infrastructure may start");
            assertTrue(windowSystem.events.isEmpty());
            if (withExistingFile) {
                assertEquals(PREVIOUS_SESSION, Files.readString(harness.logFile()));
            }
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    // --- Record format and synchronous delivery -------------------------------------------

    @Test
    void recordIsSynchronousUtf8WithAllFields() throws Exception {
        LogHarness harness = new LogHarness(temp);
        LoggingSystem logging = LoggingSystem.initialize(LoggingMode.DEFAULT, harness.location(),
                FileOperations.SYSTEM, harness.stderr);
        try {
            Logger logger = LoggerFactory.getLogger("com.example.FormatProbe");
            logger.error("Příliš žluťoučký kůň — 日本語", new IllegalStateException("format-probe-failure"));

            // Inspect while logging is still open: the record must already be in the file.
            List<String> records = LogHarness.splitRecords(LogHarness.readUtf8(harness.logFile()));
            assertEquals(1, records.size(), records.toString());
            String record = records.get(0);
            assertTrue(record.matches("(?s)^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}(Z|[+-]\\d{2}:\\d{2}) "
                    + "ERROR \\[" + Thread.currentThread().getName() + "] com\\.example\\.FormatProbe - "
                    + "Příliš žluťoučký kůň — 日本語\n.*"), record);
            assertTrue(record.contains("java.lang.IllegalStateException: format-probe-failure"), record);
            assertTrue(record.contains("\tat com.pidluzsnij.strategy.logging.LoggingSystemTest"), record);
        } finally {
            logging.close();
        }
    }

    // --- Mode filtering and default selection ---------------------------------------------

    @ParameterizedTest
    @EnumSource(LoggingMode.class)
    void modeFiltersLevels(LoggingMode mode) throws Exception {
        LogHarness harness = new LogHarness(temp);
        LoggingSystem logging = LoggingSystem.initialize(mode, harness.location(), FileOperations.SYSTEM,
                harness.stderr);
        try {
            Logger logger = LoggerFactory.getLogger("test.modes");
            logger.trace("level-trace");
            logger.debug("level-debug");
            logger.info("level-info");
            logger.warn("level-warn");
            logger.error("level-error");
        } finally {
            logging.close();
        }

        String log = harness.log();
        boolean development = mode == LoggingMode.DEVELOPMENT;
        assertEquals(development, log.contains("TRACE [") && log.contains("level-trace"));
        assertEquals(development, log.contains("DEBUG [") && log.contains("level-debug"));
        assertTrue(log.contains("level-info"));
        assertTrue(log.contains("level-warn"));
        assertTrue(log.contains("level-error"));
    }

    @Test
    void normalStartupSelectsDefaultMode() {
        LogHarness harness = new LogHarness(temp);

        int exit = harness.launch(com.pidluzsnij.strategy.ApplicationLauncher.NORMAL_STARTUP_MODE,
                new FakeWindowSystem());

        assertEquals(0, exit);
        assertTrue(harness.log().contains("INFO  [" + Thread.currentThread().getName()
                + "] com.pidluzsnij.strategy.ApplicationLauncher - Application startup begins (logging mode: default)"),
                harness.log());
    }

    // --- Logging write failure during execution -------------------------------------------

    @Test
    void writeFailureIsReportedAndCleanupStillRuns() throws Exception {
        LogHarness harness = new LogHarness(temp);
        AtomicBoolean failWrites = new AtomicBoolean();
        FileOperations failing = new FileOperations() {
            @Override
            public void write(FileChannel channel, ByteBuffer data) throws IOException {
                if (failWrites.get()) {
                    throw new IOException("simulated disk full");
                }
                FileOperations.super.write(channel, data);
            }
        };
        FakeWindowSystem windowSystem = new FakeWindowSystem();
        windowSystem.iterationsBeforeClose = 1;
        windowSystem.onProcessEvents = () -> {
            failWrites.set(true);
            LoggerFactory.getLogger("test").warn("record during failure");
        };

        int exit = harness.launch(LoggingMode.DEFAULT, failing, harness.location(), windowSystem);

        String stderr = harness.stderr();
        assertTrue(stderr.contains("Failed to write diagnostic log record"), stderr);
        assertTrue(stderr.contains("simulated disk full"), stderr);
        assertTrue(stderr.contains(harness.logFile().toAbsolutePath().toString()), stderr);
        assertTrue(windowSystem.events.contains("destroyWindow"), "cleanup must still be attempted");
        assertTrue(windowSystem.events.contains("terminate"), "cleanup must still be attempted");
        assertEquals(0, exit);

        String log = harness.log();
        assertTrue(log.contains("Application startup begins"), "earlier records must not be erased");
        assertTrue(log.contains("Window opened displaying black"), "earlier records must not be erased");
        assertFalse(log.contains("record during failure"));
        assertEquals(1, count(log, "Application startup begins"));
    }

    @Test
    void writeFailureDuringCleanupIsReportedAndCleanupCompletes() throws Exception {
        LogHarness harness = new LogHarness(temp);
        AtomicBoolean failWrites = new AtomicBoolean();
        FileOperations failing = new FileOperations() {
            @Override
            public void write(FileChannel channel, ByteBuffer data) throws IOException {
                if (failWrites.get()) {
                    throw new IOException("simulated disk full during cleanup");
                }
                FileOperations.super.write(channel, data);
            }
        };
        FakeWindowSystem windowSystem = new FakeWindowSystem();
        windowSystem.onDestroyWindow = () -> {
            failWrites.set(true);
            LoggerFactory.getLogger("test").warn("record during cleanup failure");
        };

        int exit = harness.launch(LoggingMode.DEFAULT, failing, harness.location(), windowSystem);

        String stderr = harness.stderr();
        assertTrue(stderr.contains("Failed to write diagnostic log record"), stderr);
        assertTrue(stderr.contains("simulated disk full during cleanup"), stderr);
        assertTrue(stderr.contains(harness.logFile().toAbsolutePath().toString()), stderr);
        assertEquals(List.of("initialize", "startingMonitor", "createWindow", "initializeGraphics",
                "createUiGraphics", "ui-initialize", "setPointerListener", "ui-close", "destroyWindow", "terminate"),
                windowSystem.events, "cleanup must run to completion");
        assertEquals(0, exit);

        String log = harness.log();
        assertTrue(log.contains("Application startup begins"), "earlier records must not be erased");
        assertTrue(log.contains("Window opened displaying black"), "earlier records must not be erased");
        assertFalse(log.contains("record during cleanup failure"));

        // Ownership was still released: the file can be owned again.
        LoggingSystem.initialize(LoggingMode.DEFAULT, harness.location(), FileOperations.SYSTEM, harness.stderr)
                .close();
    }

    // --- Initialization resources released on Error --------------------------------------

    @Test
    void errorDuringTruncationReleasesOwnershipAndClosesTheFile() throws Exception {
        assertErrorReleasesResources(new FileOperations() {
            @Override
            public void truncate(FileChannel channel) {
                throw new AssertionError("simulated error during truncation");
            }
        }, "simulated error during truncation", true);
    }

    @Test
    void errorDuringOwnershipAcquisitionClosesTheFile() throws Exception {
        assertErrorReleasesResources(new FileOperations() {
            @Override
            public FileLock tryLock(FileChannel channel) {
                throw new AssertionError("simulated error during locking");
            }
        }, "simulated error during locking", false);
    }

    private void assertErrorReleasesResources(FileOperations failing, String message, boolean lockAcquired)
            throws Exception {
        LogHarness harness = new LogHarness(temp);
        List<String> events = new java.util.ArrayList<>();
        FileOperations recording = new FileOperations() {
            @Override
            public FileLock tryLock(FileChannel channel) throws IOException {
                FileLock lock = failing.tryLock(channel);
                events.add("locked");
                return lock;
            }

            @Override
            public void truncate(FileChannel channel) throws IOException {
                failing.truncate(channel);
            }

            @Override
            public void release(FileLock lock) throws IOException {
                events.add("release");
                FileOperations.super.release(lock);
            }

            @Override
            public void close(FileChannel channel) throws IOException {
                events.add("close");
                FileOperations.super.close(channel);
            }
        };

        AssertionError error = assertThrows(AssertionError.class, () ->
                LoggingSystem.initialize(LoggingMode.DEFAULT, harness.location(), recording, harness.stderr));

        assertEquals(message, error.getMessage());
        assertEquals(lockAcquired ? List.of("locked", "release", "close") : List.of("close"), events);
        LoggingSystem.initialize(LoggingMode.DEFAULT, harness.location(), FileOperations.SYSTEM, harness.stderr)
                .close();
    }

    @Test
    void closeReleasesOwnershipSoTheFileCanBeOwnedAgain() throws Exception {
        LogHarness harness = new LogHarness(temp);
        LoggingSystem.initialize(LoggingMode.DEFAULT, harness.location(), FileOperations.SYSTEM, harness.stderr)
                .close();

        LoggingSystem again = LoggingSystem.initialize(LoggingMode.DEFAULT, harness.location(),
                FileOperations.SYSTEM, harness.stderr);
        again.close();
        assertEquals("", Files.readString(harness.logFile(), StandardCharsets.UTF_8));
    }
}
