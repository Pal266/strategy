package com.pidluzsnij.strategy.logging;

import com.pidluzsnij.strategy.testsupport.LogHarness;
import com.pidluzsnij.strategy.testsupport.ProbeProcess;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Behavior across separate application processes. */
class LoggingProcessTest {

    private static final long TIMEOUT_SECONDS = 60;

    @TempDir
    Path temp;

    private Path configDirectory() {
        return temp.resolve("isolated-user").resolve("config");
    }

    private Path logFile() {
        return configDirectory().resolve("strategy").resolve("log.log");
    }

    private Path workingDirectory(String name) throws Exception {
        return Files.createDirectories(temp.resolve(name));
    }

    // --- Log location and first startup ---------------------------------------------------

    @Test
    void separateRunsFromDifferentWorkingDirectoriesUseTheSameLogFile() throws Exception {
        assertFalse(Files.exists(configDirectory().resolve("strategy")));

        for (String run : List.of("cwd-one", "cwd-two")) {
            Path cwd = workingDirectory(run);
            ProbeProcess probe = ProbeProcess.start(cwd, "run", configDirectory().toString(), "marker-" + run);
            assertEquals(0, probe.awaitExit(TIMEOUT_SECONDS), probe.stderr());

            String infra = probe.stdout().stream().filter(l -> l.startsWith("INFRA_STARTED")).findFirst()
                    .orElseThrow(() -> new AssertionError("infrastructure did not start: " + probe.stdout()));
            assertFalse(infra.endsWith("missing"), "log file must exist before other infrastructure starts");
            assertFalse(infra.endsWith("logSize=0"), "logging must be ready before other infrastructure starts");

            String log = LogHarness.readUtf8(logFile());
            assertTrue(log.contains("MARKER marker-" + run), log);
            assertTrue(log.indexOf("Application startup begins") < log.indexOf("MARKER marker-" + run));
            try (Stream<Path> files = Files.list(cwd)) {
                assertEquals(0, files.count(), "nothing is written to the working directory");
            }
        }
    }

    @Test
    void productionLocationIsTheSamePerUserDirectoryFromAnyWorkingDirectory() throws Exception {
        Map<String, String> environment = Map.of();
        boolean linux = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("linux");
        Path isolatedConfig = temp.resolve("xdg-config");
        if (linux) {
            environment = Map.of("XDG_CONFIG_HOME", isolatedConfig.toString());
        }

        String first = location(workingDirectory("loc-one"), environment);
        String second = location(workingDirectory("loc-two"), environment);

        assertEquals(first, second);
        assertTrue(Path.of(first).isAbsolute(), first);
        if (linux) {
            assertEquals(isolatedConfig.toString(), first);
        }
    }

    private static String location(Path cwd, Map<String, String> environment) throws Exception {
        ProbeProcess probe = ProbeProcess.start(cwd, environment, "location");
        assertEquals(0, probe.awaitExit(TIMEOUT_SECONDS), probe.stderr());
        return probe.stdout().stream().filter(l -> l.startsWith("LOCATION ")).findFirst()
                .orElseThrow().substring("LOCATION ".length());
    }

    // --- Exclusive ownership across processes, shutdown ordering --------------------------

    @Test
    void onlyOneProcessOwnsTheLogFileAtATime() throws Exception {
        Path cwd = workingDirectory("cwd");
        ProbeProcess first = ProbeProcess.start(cwd, "hold", configDirectory().toString(), "first-owner");
        try {
            first.awaitLine("READY", TIMEOUT_SECONDS);
            String firstLog = LogHarness.readUtf8(logFile());
            assertTrue(firstLog.contains("MARKER first-owner"), firstLog);

            ProbeProcess second = ProbeProcess.start(cwd, "run", configDirectory().toString(), "second-rejected");
            assertEquals(1, second.awaitExit(TIMEOUT_SECONDS));
            assertTrue(second.stderr().contains("already in use"), second.stderr());
            assertTrue(second.stderr().contains(logFile().toAbsolutePath().toString()), second.stderr());
            assertTrue(second.stdout().stream().noneMatch(l -> l.startsWith("INFRA_STARTED")),
                    "rejected instance must abort before other infrastructure");
            assertEquals(firstLog, LogHarness.readUtf8(logFile()), "rejected instance must not alter the log");

            first.sendLine();
            assertEquals(0, first.awaitExit(TIMEOUT_SECONDS), first.stderr());
        } finally {
            first.destroy();
        }

        String finalFirstLog = LogHarness.readUtf8(logFile());
        List<String> records = LogHarness.splitRecords(finalFirstLog);
        assertTrue(records.get(records.size() - 1).contains("Normal shutdown completed"),
                "final record is written before ownership is released");
        assertFalse(finalFirstLog.contains("second-rejected"));

        ProbeProcess third = ProbeProcess.start(cwd, "run", configDirectory().toString(), "third-owner");
        assertEquals(0, third.awaitExit(TIMEOUT_SECONDS), third.stderr());
        String thirdLog = LogHarness.readUtf8(logFile());
        assertTrue(thirdLog.contains("MARKER third-owner"));
        assertFalse(thirdLog.contains("first-owner"), "previous contents are replaced");
    }
}
