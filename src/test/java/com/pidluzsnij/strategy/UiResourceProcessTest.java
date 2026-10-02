package com.pidluzsnij.strategy;

import com.pidluzsnij.strategy.testsupport.ProbeProcess;
import com.pidluzsnij.strategy.testsupport.UiFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * UI resource locations in separate processes started from unrelated working directories: bundled resources
 * come from the runtime class path (directory or archive) and overrides from the per-user {@code strategy/ui}
 * directory, never from the working directory, the source tree or an environment variable.
 */
class UiResourceProcessTest {

    @TempDir
    Path temp;

    private static final byte[] A = "bundled-A".getBytes(StandardCharsets.UTF_8);
    private static final byte[] B = "bundled-B".getBytes(StandardCharsets.UTF_8);
    private static final byte[] B_OVERRIDE = "external-B".getBytes(StandardCharsets.UTF_8);
    private static final byte[] DECOY = "decoy".getBytes(StandardCharsets.UTF_8);

    /** Working directories, one of them holding decoy {@code ui} and {@code strategy/ui} folders. */
    private List<Path> workingDirectories() throws Exception {
        Path plain = Files.createDirectories(temp.resolve("wd-plain"));
        Path decoy = Files.createDirectories(temp.resolve("wd-decoy"));
        UiFixtures.resources().put("a/A.bin", DECOY).put("b/B.bin", DECOY).writeTo(decoy.resolve("ui"));
        UiFixtures.resources().put("a/A.bin", DECOY).put("b/B.bin", DECOY).writeTo(decoy.resolve("strategy").resolve("ui"));
        UiFixtures.resources().put("a/A.bin", DECOY).writeTo(decoy.resolve("src/main/resources/ui"));
        return List.of(plain, decoy);
    }

    private List<String> resolve(Path workingDirectory, Path bundledRoot, Path configBase) throws Exception {
        ProbeProcess probe = ProbeProcess.start(workingDirectory,
                Map.of("STRATEGY_UI", temp.resolve("wd-decoy").toString(), "UI_ROOT", temp.resolve("wd-decoy").toString()),
                "uiresolve", bundledRoot.toString(), configBase.toString(), "a/A.bin", "b/B.bin");
        assertEquals(0, probe.awaitExit(60), probe.stderr());
        return probe.stdout().stream().filter(line -> line.startsWith("RESOLVED ")).toList();
    }

    @Test
    void bundledResourcesLoadFromDirectoryAndArchiveIndependentOfWorkingDirectory() throws Exception {
        UiFixtures.ResourceSet set = UiFixtures.resources().put("a/A.bin", A).put("b/B.bin", B);
        Path directory = set.writeBundledDirectory(temp.resolve("exploded"));
        Path archive = set.writeBundledArchive(temp.resolve("archive/ui-resources.jar"));
        Path noOverrides = temp.resolve("config-empty");
        List<String> expected = List.of(
                "RESOLVED a/A.bin BUNDLED " + UiFixtures.sha256(A),
                "RESOLVED b/B.bin BUNDLED " + UiFixtures.sha256(B));

        for (Path workingDirectory : workingDirectories()) {
            assertEquals(expected, resolve(workingDirectory, directory, noOverrides), workingDirectory.toString());
            assertEquals(expected, resolve(workingDirectory, archive, noOverrides), workingDirectory.toString());
        }
        assertEquals(false, Files.exists(noOverrides), "no external directory is created");
    }

    @Test
    void overridesComeFromTheUiDirectoryOfTheIsolatedApplicationDirectory() throws Exception {
        Path bundled = UiFixtures.resources().put("a/A.bin", A).put("b/B.bin", B)
                .writeBundledDirectory(temp.resolve("exploded"));
        Path configBase = temp.resolve("config");
        Path strategy = Files.createDirectories(configBase.resolve("strategy"));
        Files.writeString(strategy.resolve("application-settings.toml"), "[video]\nfullscreen = true\n");
        Files.writeString(strategy.resolve("log.log"), "previous log\n");
        UiFixtures.resources().put("b/B.bin", B_OVERRIDE).writeExternal(configBase);
        List<String> expected = List.of(
                "RESOLVED a/A.bin BUNDLED " + UiFixtures.sha256(A),
                "RESOLVED b/B.bin EXTERNAL " + UiFixtures.sha256(B_OVERRIDE));

        for (Path workingDirectory : workingDirectories()) {
            assertEquals(expected, resolve(workingDirectory, bundled, configBase), workingDirectory.toString());
        }
        assertEquals("[video]\nfullscreen = true\n", Files.readString(strategy.resolve("application-settings.toml")));
        assertEquals("previous log\n", Files.readString(strategy.resolve("log.log")));
    }
}
