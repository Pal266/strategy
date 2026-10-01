package com.pidluzsnij.strategy;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Java, Maven, LWJGL, native-library and JUnit version constraints. */
class BuildConstraintsTest {

    static String jarName(Class<?> type) throws Exception {
        return Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).getFileName().toString();
    }

    @Test
    void runsOnJava21AndCompilesForJava21() throws Exception {
        assertTrue(Runtime.version().feature() >= 21, "Maven must run with Java 21 or newer");
        try (InputStream in = Main.class.getResourceAsStream("Main.class")) {
            assertNotNull(in);
            byte[] header = in.readNBytes(8);
            int major = ((header[6] & 0xFF) << 8) | (header[7] & 0xFF);
            assertEquals(65, major, "class files must target Java 21");
        }
    }

    @Test
    void testsExecuteWithJUnitJupiter613() throws Exception {
        assertEquals("junit-jupiter-api-6.1.3.jar", jarName(org.junit.jupiter.api.Test.class));
        assertEquals("junit-jupiter-engine-6.1.3.jar",
                jarName(Class.forName("org.junit.jupiter.engine.JupiterTestEngine")));
    }

    @Test
    void lwjglModulesResolveAt343() throws Exception {
        assertEquals("lwjgl-3.4.3.jar", jarName(org.lwjgl.Version.class));
        assertEquals("lwjgl-glfw-3.4.3.jar", jarName(org.lwjgl.glfw.GLFW.class));
        assertEquals("lwjgl-opengl-3.4.3.jar", jarName(org.lwjgl.opengl.GL.class));
        assertTrue(org.lwjgl.Version.getVersion().startsWith("3.4.3"), org.lwjgl.Version.getVersion());
    }

    @Test
    void nativesForThisPlatformResolveAt343() {
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch").toLowerCase(Locale.ROOT);
        boolean arm64 = arch.equals("aarch64") || arch.equals("arm64");
        boolean x86 = arch.equals("x86") || arch.equals("i386") || arch.equals("i686");

        String platform;
        String classifier;
        String prefix;
        String suffix;
        if (os.contains("win")) {
            platform = "windows";
            classifier = arm64 ? "natives-windows-arm64" : x86 ? "natives-windows-x86" : "natives-windows";
            prefix = "";
            suffix = ".dll";
        } else if (os.contains("mac")) {
            platform = "macos";
            classifier = arm64 ? "natives-macos-arm64" : "natives-macos";
            prefix = "lib";
            suffix = ".dylib";
        } else {
            platform = "linux";
            classifier = arm64 ? "natives-linux-arm64" : "natives-linux";
            prefix = "lib";
            suffix = ".so";
        }
        String archDirectory = arm64 ? "arm64" : x86 ? "x86" : "x64";

        assertNative(platform, archDirectory, "", prefix + "lwjgl" + suffix, "lwjgl-3.4.3-" + classifier + ".jar");
        assertNative(platform, archDirectory, "glfw/", prefix + "glfw" + suffix,
                "lwjgl-glfw-3.4.3-" + classifier + ".jar");
        if (platform.equals("macos")) {
            assertNative(platform, archDirectory, "glfw/", "libglfw_async.dylib",
                    "lwjgl-glfw-3.4.3-" + classifier + ".jar");
        }
        assertNative(platform, archDirectory, "opengl/", prefix + "lwjgl_opengl" + suffix,
                "lwjgl-opengl-3.4.3-" + classifier + ".jar");
    }

    private static void assertNative(String platform, String arch, String module, String library, String jar) {
        String resource = platform + "/" + arch + "/org/lwjgl/" + module + library;
        URL url = BuildConstraintsTest.class.getClassLoader().getResource(resource);
        assertNotNull(url, "native library missing from classpath: " + resource);
        assertTrue(url.toString().contains("/" + jar + "!"), "expected " + resource + " from " + jar + " but was " + url);
    }
}
