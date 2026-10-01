package com.pidluzsnij.strategy;

import com.pidluzsnij.strategy.logging.FileOperations;
import com.pidluzsnij.strategy.testsupport.LogHarness;
import com.pidluzsnij.strategy.testsupport.ProbeProcess;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Configuration initialization in separate application processes. */
class ConfigurationProcessTest {

    private static final long TIMEOUT_SECONDS = 60;

    @TempDir
    Path temp;

    private Path configDirectory() {
        return temp.resolve("isolated-user").resolve("config");
    }

    private Path settingsFile() {
        return configDirectory().resolve("strategy").resolve("application-settings.toml");
    }

    private Path logFile() {
        return configDirectory().resolve("strategy").resolve("log.log");
    }

    /** On Linux, redirects the production per-user directories to an observed sentinel. */
    private Map<String, String> sentinelEnvironment(Path sentinel) {
        if (!System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("linux")) {
            return Map.of();
        }
        return Map.of("XDG_CONFIG_HOME", sentinel.resolve("xdg-config").toString(),
                "HOME", sentinel.resolve("home").toString());
    }

    private static void assertEmpty(Path sentinel) throws Exception {
        if (Files.exists(sentinel)) {
            try (Stream<Path> files = Files.walk(sentinel)) {
                assertEquals(List.of(sentinel), files.toList(), "production locations were accessed");
            }
        }
    }

    @Test
    void firstRunCreatesConfigurationBeforeWindowStartup() throws Exception {
        Path sentinel = temp.resolve("sentinel");
        Path cwd = Files.createDirectories(temp.resolve("cwd"));

        ProbeProcess probe = ProbeProcess.start(cwd, sentinelEnvironment(sentinel), "run",
                configDirectory().toString(), "first-run");

        assertEquals(0, probe.awaitExit(TIMEOUT_SECONDS), probe.stderr());
        assertTrue(Files.isRegularFile(settingsFile()));
        assertTrue(probe.stdout().stream().anyMatch(l -> l.startsWith("INFRA_STARTED")));
        String log = LogHarness.readUtf8(logFile());
        assertTrue(log.indexOf("Configuration initialized") < log.indexOf("MARKER first-run"), log);
        assertTrue(log.contains("Normal shutdown completed"));
        assertEmpty(sentinel);
    }

    @Test
    void fatalConfigurationFailureEndsStartupAndReleasesTheLog() throws Exception {
        Path sentinel = temp.resolve("sentinel");
        Path cwd = Files.createDirectories(temp.resolve("cwd"));
        Files.createDirectories(settingsFile().getParent());
        Files.writeString(settingsFile(), "[broken");
        byte[] original = Files.readAllBytes(settingsFile());

        ProbeProcess probe = ProbeProcess.start(cwd, sentinelEnvironment(sentinel), "run",
                configDirectory().toString(), "fatal");

        assertEquals(1, probe.awaitExit(TIMEOUT_SECONDS), probe.stderr());
        assertTrue(probe.stdout().stream().noneMatch(l -> l.startsWith("INFRA_STARTED")),
                "window startup does not occur: " + probe.stdout());
        assertArrayEquals(original, Files.readAllBytes(settingsFile()));
        String log = LogHarness.readUtf8(logFile());
        assertEquals(1, LogHarness.count(log, "Configuration initialization failed"), log);
        assertTrue(log.contains("parse configuration file"));
        assertFalse(log.contains("Normal shutdown completed"));
        assertFalse(log.contains("MARKER fatal"));
        try (FileChannel channel = FileChannel.open(logFile(), StandardOpenOption.WRITE)) {
            FileLock lock = channel.tryLock(FileOperations.OWNERSHIP_LOCK_POSITION, 1, false);
            assertNotNull(lock, "log ownership was released by the terminated process");
            lock.release();
        }
        assertEmpty(sentinel);
    }

    @Test
    void missingConfigurationLibraryIsLoggedAsAConfigurationFailure() throws Exception {
        String classpath = Stream.of(System.getProperty("java.class.path").split(File.pathSeparator))
                .filter(entry -> !entry.contains("night-config"))
                .collect(Collectors.joining(File.pathSeparator));
        assertFalse(classpath.contains("night-config"));
        Path cwd = Files.createDirectories(temp.resolve("cwd"));

        ProbeProcess probe = ProbeProcess.startWithClasspath(cwd, Map.of(), classpath, "run",
                configDirectory().toString(), "no-library");

        assertEquals(1, probe.awaitExit(TIMEOUT_SECONDS), probe.stderr());
        assertTrue(probe.stdout().contains("EXIT 1"), "the launcher returned normally: " + probe.stdout());
        assertTrue(probe.stdout().stream().noneMatch(l -> l.startsWith("INFRA_STARTED")));
        assertFalse(probe.stderr().contains("NoClassDefFoundError"), "nothing escapes before logging: "
                + probe.stderr());
        String log = LogHarness.readUtf8(logFile());
        assertEquals(1, LogHarness.count(log, "Configuration initialization failed"), log);
        assertTrue(log.contains("create configuration persistence"), log);
        assertTrue(log.contains("java.lang.NoClassDefFoundError"), log);
        assertFalse(log.contains("Normal shutdown completed"));
        assertFalse(Files.exists(settingsFile()));
    }
}
