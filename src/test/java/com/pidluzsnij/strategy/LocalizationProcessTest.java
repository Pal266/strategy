package com.pidluzsnij.strategy;

import com.pidluzsnij.strategy.logging.FileOperations;
import com.pidluzsnij.strategy.testsupport.LogHarness;
import com.pidluzsnij.strategy.testsupport.ProbeProcess;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Localization in separate application processes with isolated runtime class paths. */
class LocalizationProcessTest {

    private static final long TIMEOUT_SECONDS = 60;

    private static final Map<String, String> FIXTURE = Map.of(
            "languages.csv", """
                    language_identifier,displayed_name,localization_file
                    en,English,english.properties
                    uk,Українська,ukrainian.properties
                    cs,Čeština,czech.properties
                    hu,Magyar,hungarian.properties
                    """,
            "english.properties", "test.key=from-fixture-en\n",
            "ukrainian.properties", "test.key=з-фікстури\n",
            "czech.properties", "",
            "hungarian.properties", "");

    @TempDir
    Path temp;

    private static Path writeExploded(Path root, Map<String, String> files) throws Exception {
        Path folder = Files.createDirectories(root.resolve("localization"));
        for (Map.Entry<String, String> file : files.entrySet()) {
            Files.writeString(folder.resolve(file.getKey()), file.getValue(), StandardCharsets.UTF_8);
        }
        return root;
    }

    private static Path writeArchive(Path jar, Map<String, String> files) throws Exception {
        try (OutputStream out = Files.newOutputStream(jar); JarOutputStream archive = new JarOutputStream(out)) {
            for (Map.Entry<String, String> file : files.entrySet()) {
                archive.putNextEntry(new JarEntry("localization/" + file.getKey()));
                archive.write(file.getValue().getBytes(StandardCharsets.UTF_8));
                archive.closeEntry();
            }
        }
        return jar;
    }

    @Test
    void resourcesLoadFromTheRuntimeClassPathIndependentlyOfTheWorkingDirectory() throws Exception {
        Path exploded = writeExploded(temp.resolve("exploded"), FIXTURE);
        Path archive = writeArchive(temp.resolve("resources.jar"), FIXTURE);
        Path unrelated = Files.createDirectories(temp.resolve("unrelated").resolve("deeper"));
        // A decoy localization folder in a working directory must never be used.
        Path decoy = writeExploded(temp.resolve("decoy"), Map.of("languages.csv", "broken\n",
                "ukrainian.properties", "test.key=decoy\n"));

        for (Path root : List.of(exploded, archive)) {
            for (Path cwd : List.of(unrelated, decoy, temp)) {
                ProbeProcess probe = ProbeProcess.start(cwd, "localize", root.toString(), "uk", "test.key");
                assertEquals(0, probe.awaitExit(TIMEOUT_SECONDS), probe.stderr());
                List<String> out = probe.stdout();
                String context = root + " from " + cwd + ": " + out + probe.stderr();
                assertTrue(out.contains("LANGUAGES en=English;uk=Українська;cs=Čeština;hu=Magyar"), context);
                assertTrue(out.contains("EFFECTIVE uk"), context);
                assertTrue(out.contains("TEXT з-фікстури"), context);
            }
        }
    }

    /** On Linux, redirects the production per-user directories to an observed sentinel. */
    private Map<String, String> sentinelEnvironment(Path sentinel) {
        if (!System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("linux")) {
            return Map.of();
        }
        return Map.of("XDG_CONFIG_HOME", sentinel.resolve("xdg-config").toString(),
                "HOME", sentinel.resolve("home").toString());
    }

    @Test
    void fatalLocalizationFailureTerminatesAndReleasesTheLogForTheNextProcess() throws Exception {
        Path configDirectory = temp.resolve("isolated-user").resolve("config");
        Path logFile = configDirectory.resolve("strategy").resolve("log.log");
        Path cwd = Files.createDirectories(temp.resolve("cwd"));
        Path sentinel = temp.resolve("sentinel");
        Path noMetadata = writeExploded(temp.resolve("no-metadata"), Map.of("english.properties", ""));
        Path brokenEnglish = writeExploded(temp.resolve("broken-english"),
                Map.of("languages.csv", FIXTURE.get("languages.csv"), "english.properties", "no separator\n"));

        for (Path resources : List.of(noMetadata, brokenEnglish)) {
            ProbeProcess probe = ProbeProcess.start(cwd, sentinelEnvironment(sentinel), "run",
                    configDirectory.toString(), "fatal", resources.toString());

            assertEquals(1, probe.awaitExit(TIMEOUT_SECONDS), probe.stderr());
            assertTrue(probe.stdout().contains("EXIT 1"), probe.stdout().toString());
            assertTrue(probe.stdout().stream().noneMatch(l -> l.startsWith("INFRA_STARTED")),
                    "window startup does not occur: " + probe.stdout());
            String log = LogHarness.readUtf8(logFile);
            assertEquals(1, LogHarness.count(log, "Localization initialization failed"), log);
            assertFalse(log.contains("Localization initialized"));
            assertFalse(log.contains("Normal shutdown completed"));
            try (FileChannel channel = FileChannel.open(logFile, StandardOpenOption.WRITE)) {
                FileLock lock = channel.tryLock(FileOperations.OWNERSHIP_LOCK_POSITION, 1, false);
                assertNotNull(lock, "log ownership was released by the terminated process");
                lock.release();
            }

            ProbeProcess next = ProbeProcess.start(cwd, sentinelEnvironment(sentinel), "run",
                    configDirectory.toString(), "next");
            assertEquals(0, next.awaitExit(TIMEOUT_SECONDS), next.stderr());
            String nextLog = LogHarness.readUtf8(logFile);
            assertTrue(nextLog.contains("MARKER next"), "the next process acquired the log: " + nextLog);
            assertTrue(nextLog.contains("Localization initialized"), nextLog);
        }
        if (Files.exists(sentinel)) {
            try (Stream<Path> files = Files.walk(sentinel)) {
                assertEquals(List.of(sentinel), files.toList(), "production locations were accessed");
            }
        }
    }
}
