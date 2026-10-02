package com.pidluzsnij.strategy.ui.resource;

import com.pidluzsnij.strategy.config.persistence.toml.TomlConfigurationPersistence;
import com.pidluzsnij.strategy.logging.LoggingSystem;
import com.pidluzsnij.strategy.testsupport.UiFixtures;
import com.pidluzsnij.strategy.ui.UiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URLClassLoader;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Per-resource resolution between bundled resources and external overrides. */
class UiResourcesTest {

    @TempDir
    Path temp;

    private static final byte[] A = "bundled-A".getBytes(StandardCharsets.UTF_8);
    private static final byte[] B = "bundled-B".getBytes(StandardCharsets.UTF_8);
    private static final byte[] C = "bundled-C".getBytes(StandardCharsets.UTF_8);
    private static final byte[] B_OVERRIDE = "external-B".getBytes(StandardCharsets.UTF_8);

    private URLClassLoader bundled() {
        return UiFixtures.classLoader(UiFixtures.resources()
                .put("a/A.bin", A).put("b/B.bin", B).put("C.bin", C)
                .writeBundledDirectory(temp.resolve("classpath")));
    }

    private Path configBase() {
        return temp.resolve("config");
    }

    private UiResources resources(ClassLoader loader) {
        return new UiResources(loader, UiResources.externalRoot(configBase()));
    }

    @Test
    void externalRootIsTheUiDirectoryOfTheSharedApplicationDirectory() {
        Path base = temp.resolve("base");
        assertEquals(base.toAbsolutePath().resolve("strategy").resolve("ui"), UiResources.externalRoot(base));
        assertEquals(TomlConfigurationPersistence.DIRECTORY_NAME, UiResources.APPLICATION_DIRECTORY);
        assertEquals(LoggingSystem.DIRECTORY_NAME, UiResources.APPLICATION_DIRECTORY);
        assertEquals(TomlConfigurationPersistence.settingsFile(base).getParent(), UiResources.externalRoot(base).getParent());
    }

    @Test
    void partialOverridesResolveEachResourceIndependently() throws Exception {
        UiFixtures.resources().put("b/B.bin", B_OVERRIDE).writeExternal(configBase());
        try (URLClassLoader loader = bundled()) {
            UiResources resources = resources(loader);

            ResolvedUiResource a = resources.resolve(UiResourcePath.of("a/A.bin"));
            ResolvedUiResource b = resources.resolve(UiResourcePath.of("b/B.bin"));
            ResolvedUiResource c = resources.resolve(UiResourcePath.of("C.bin"));

            assertEquals(UiResourceOrigin.BUNDLED, a.origin());
            assertArrayEquals(A, a.bytes());
            assertEquals(UiResourceOrigin.EXTERNAL, b.origin());
            assertArrayEquals(B_OVERRIDE, b.bytes());
            assertEquals(UiResourceOrigin.BUNDLED, c.origin());
            assertArrayEquals(C, c.bytes());
        }
        assertFalse(Files.exists(UiFixtures.externalRoot(configBase()).resolve("a")), "no copies are created");
        assertFalse(Files.exists(UiFixtures.externalRoot(configBase()).resolve("C.bin")));
    }

    @Test
    void absentExternalRootOrFileUsesBundledResources() throws Exception {
        try (URLClassLoader loader = bundled()) {
            assertEquals(UiResourceOrigin.BUNDLED, resources(loader).resolve(UiResourcePath.of("C.bin")).origin());
            assertFalse(Files.exists(UiFixtures.externalRoot(configBase())), "the external root is not created");

            Files.createDirectories(UiFixtures.externalRoot(configBase()).resolve("b"));
            ResolvedUiResource b = resources(loader).resolve(UiResourcePath.of("b/B.bin"));
            assertEquals(UiResourceOrigin.BUNDLED, b.origin());
            assertArrayEquals(B, b.bytes());
        }
    }

    @Test
    void missingEverywhereFailsWithSafeContext() throws Exception {
        try (URLClassLoader loader = bundled()) {
            UiException failure = assertThrows(UiException.class,
                    () -> resources(loader).resolve(UiResourcePath.of("missing/D.bin")));
            assertEquals("resolve UI resource", failure.stage());
            assertEquals("missing/D.bin", failure.resource());
            assertTrue(failure.reason().contains("neither"));
        }
    }

    @Test
    void existingButUnreadableOverrideFailsWithoutBundledFallback() throws Exception {
        Path external = UiFixtures.externalRoot(configBase());
        Files.createDirectories(external.resolve("b").resolve("B.bin"));
        try (URLClassLoader loader = bundled()) {
            UiException failure = assertThrows(UiException.class, () -> resources(loader).resolve(UiResourcePath.of("b/B.bin")));
            assertEquals("external b/B.bin", failure.resource());
        }
    }

    @Test
    void permissionDeniedOverrideFailsWithoutBundledFallback() throws Exception {
        Path file = UiFixtures.resources().put("b/B.bin", B_OVERRIDE).writeExternal(configBase()).resolve("b/B.bin");
        assumeTrue(file.toFile().setReadable(false, false) && !Files.isReadable(file),
                "the file system cannot make a file unreadable for this user (Windows uses the locked-file test)");
        try (URLClassLoader loader = bundled()) {
            UiException failure = assertThrows(UiException.class, () -> resources(loader).resolve(UiResourcePath.of("b/B.bin")));
            assertEquals("external b/B.bin", failure.resource());
            assertTrue(failure.reason().contains("could not be read"));
        } finally {
            file.toFile().setReadable(true, false);
        }
    }

    @Test
    void lockedOverrideFailsWithoutBundledFallbackOnWindows() throws Exception {
        assumeTrue(isWindows(), "only Windows enforces exclusive file locks against readers");
        Path file = UiFixtures.resources().put("b/B.bin", B_OVERRIDE).writeExternal(configBase()).resolve("b/B.bin");
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ, StandardOpenOption.WRITE);
             FileLock lock = channel.lock();
             URLClassLoader loader = bundled()) {
            assertTrue(lock.isValid());
            UiException failure = assertThrows(UiException.class, () -> resources(loader).resolve(UiResourcePath.of("b/B.bin")));
            assertEquals("external b/B.bin", failure.resource());
            assertTrue(failure.reason().contains("could not be read"), failure.reason());
        }
    }

    @Test
    void externalRootThatIsNotADirectoryFails() throws Exception {
        Files.createDirectories(configBase().resolve("strategy"));
        Files.writeString(UiFixtures.externalRoot(configBase()), "not a directory");
        try (URLClassLoader loader = bundled()) {
            assertThrows(UiException.class, () -> resources(loader).resolve(UiResourcePath.of("C.bin")));
        }
    }

    @Test
    void symbolicLinksLeavingTheExternalRootAreRejectedBeforeReading() throws Exception {
        Path sentinel = temp.resolve("outside").resolve("sentinel.bin");
        Files.createDirectories(sentinel.getParent());
        Files.writeString(sentinel, "SENTINEL-OUTSIDE-ROOT");
        Path external = UiFixtures.externalRoot(configBase());
        Files.createDirectories(external);
        try {
            Files.createSymbolicLink(external.resolve("C.bin"), sentinel);
            Files.createSymbolicLink(external.resolve("linked"), sentinel.getParent());
        } catch (IOException | UnsupportedOperationException e) {
            assumeTrue(false, "symbolic links are not available: " + e);
        }
        try (URLClassLoader loader = bundled()) {
            UiResources resources = resources(loader);
            for (String path : new String[] {"C.bin", "linked/sentinel.bin"}) {
                UiException failure = assertThrows(UiException.class, () -> resources.resolve(UiResourcePath.of(path)));
                assertTrue(failure.reason().contains("outside"), failure.reason());
                assertFalse(failure.getMessage().contains("SENTINEL"));
            }
        }
    }

    @Test
    void directoryJunctionsLeavingTheExternalRootAreRejectedOnWindows() throws Exception {
        assumeTrue(isWindows(), "directory junctions exist only on Windows");
        Path outside = Files.createDirectories(temp.resolve("outside"));
        Files.writeString(outside.resolve("sentinel.bin"), "SENTINEL-OUTSIDE-ROOT");
        Path external = Files.createDirectories(UiFixtures.externalRoot(configBase()));
        // Unlike symbolic links, junctions need no special privilege.
        Process mklink = new ProcessBuilder("cmd", "/c", "mklink", "/J", external.resolve("linked").toString(),
                outside.toString()).redirectErrorStream(true).start();
        mklink.getInputStream().readAllBytes();
        assumeTrue(mklink.waitFor() == 0 && Files.exists(external.resolve("linked").resolve("sentinel.bin")),
                "a directory junction could not be created");
        try (URLClassLoader loader = bundled()) {
            UiException failure = assertThrows(UiException.class,
                    () -> resources(loader).resolve(UiResourcePath.of("linked/sentinel.bin")));
            assertTrue(failure.reason().contains("outside"), failure.reason());
            assertFalse(failure.getMessage().contains("SENTINEL"));
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("win");
    }

    @Test
    void bundledResourcesComeOnlyFromTheUiFolderOfTheClassPath() throws Exception {
        Path root = temp.resolve("classpath");
        UiFixtures.resources().put("x.bin", A).writeBundledDirectory(root);
        Files.writeString(root.resolve("outside.bin"), "outside");
        try (URLClassLoader loader = UiFixtures.classLoader(root)) {
            UiResources resources = resources(loader);
            assertArrayEquals(A, resources.resolve(UiResourcePath.of("x.bin")).bytes());
            assertThrows(UiException.class, () -> resources.resolve(UiResourcePath.of("outside.bin")));
        }
    }

    @Test
    void bundledArchiveAndDirectoryResolveIdentically() throws Exception {
        UiFixtures.ResourceSet set = UiFixtures.resources().put("images/x.png", UiFixtures.png(3, 2, 0x11223344));
        try (URLClassLoader directory = UiFixtures.classLoader(set.writeBundledDirectory(temp.resolve("dir")));
             URLClassLoader archive = UiFixtures.classLoader(set.writeBundledArchive(temp.resolve("ui.jar")))) {
            ResolvedUiResource fromDirectory = resources(directory).resolve(UiResourcePath.of("images/x.png"));
            ResolvedUiResource fromArchive = resources(archive).resolve(UiResourcePath.of("images/x.png"));
            assertArrayEquals(fromDirectory.bytes(), fromArchive.bytes());
        }
    }

    @Test
    void resolvedResourceNeverPrintsItsContents() {
        ResolvedUiResource resource = new ResolvedUiResource(UiResourcePath.of("a.json"), UiResourceOrigin.BUNDLED,
                "SECRET-CONTENT".getBytes(StandardCharsets.UTF_8));
        assertFalse(resource.toString().contains("SECRET"));
        assertEquals("bundled a.json", resource.name());
    }

    @Test
    void productionBundledRootContainsNoTestFixtures() {
        ClassLoader application = UiResources.class.getClassLoader();
        for (String fixture : new String[] {"ui/" + UiFixtures.FONT_A, "ui/fonts/" + UiFixtures.FONT_A,
                "ui/screens/test.json", "ui/images/x.png"}) {
            assertNull(application.getResource(fixture), fixture);
        }
    }
}
