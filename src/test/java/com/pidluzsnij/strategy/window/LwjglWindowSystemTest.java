package com.pidluzsnij.strategy.window;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.Configuration;
import org.lwjgl.system.Platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** GLFW native-library selection; no display is needed. */
class LwjglWindowSystemTest {

    @AfterEach
    void resetConfiguration() {
        Configuration.GLFW_LIBRARY_NAME.set(null);
    }

    @Test
    void macOsUsesGlfwAsyncSoNoJvmOptionIsNeeded() {
        assertEquals("glfw_async", LwjglWindowSystem.glfwLibraryNameFor(Platform.MACOSX));

        LwjglWindowSystem.selectGlfwLibrary(Platform.MACOSX);

        assertEquals("glfw_async", Configuration.GLFW_LIBRARY_NAME.get());
    }

    @Test
    void otherPlatformsKeepTheDefaultGlfwLibrary() {
        for (Platform platform : new Platform[] {Platform.WINDOWS, Platform.LINUX, Platform.FREEBSD}) {
            assertNull(LwjglWindowSystem.glfwLibraryNameFor(platform));
            LwjglWindowSystem.selectGlfwLibrary(platform);
            assertNull(Configuration.GLFW_LIBRARY_NAME.get(), platform.getName());
        }
    }

    @Test
    void explicitlyConfiguredGlfwLibraryIsKept() {
        Configuration.GLFW_LIBRARY_NAME.set("custom_glfw");

        LwjglWindowSystem.selectGlfwLibrary(Platform.MACOSX);

        assertEquals("custom_glfw", Configuration.GLFW_LIBRARY_NAME.get());
    }
}
