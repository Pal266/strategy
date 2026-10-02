package com.pidluzsnij.strategy.ui.resource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Confinement of UI resource paths to their root. */
class UiResourcePathTest {

    @ParameterizedTest
    @ValueSource(strings = {"/etc/passwd", "\\windows\\win.ini", "C:/Windows/win.ini", "C:\\Windows\\win.ini", "c:x.png",
            "\\\\server\\share\\x.png", "//server/share/x.png", "../x.png", "images/../../x.png", "images/..",
            "./x.png", "images/./x.png", "images//x.png", "images/", "", "images\\..\\x.png", "x.png:stream",
            "~/x.png", "%2e%2e/x.png", "images/x.png.", "CON", "nul.png", "images/COM1.png", "lpt9.txt",
            "file:///etc/passwd", "images/ x.png", "images/\u0000x.png", "images/ü.png", ".hidden/../../x", ".config"})
    void escapingOrUnsafePathsAreRejected(String path) {
        assertFalse(UiResourcePath.isValid(path), path);
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> UiResourcePath.of(path));
        if (!path.isEmpty()) {
            assertFalse(failure.getMessage().contains(path), "the rejected path is not quoted");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"x.png", "images/button-normal.png", "fonts/Main_Font.ttf", "screens/menu.v2.json",
            "a/b/c/d/e.png", "console.png", "nullable.ttf"})
    void confinedRelativePathsAreAccepted(String path) {
        assertTrue(UiResourcePath.isValid(path), path);
        assertEquals(path, UiResourcePath.of(path).value());
    }

    @Test
    void longPathsAreRejected() {
        assertFalse(UiResourcePath.isValid("a/".repeat(200) + "x.png"));
    }

    @Test
    void segmentsAndExtension() {
        UiResourcePath path = UiResourcePath.of("images/Button.PNG");
        assertEquals(List.of("images", "Button.PNG"), path.segments());
        assertEquals(".png", path.extension());
        assertEquals("", UiResourcePath.of("images/readme").extension());
    }
}
