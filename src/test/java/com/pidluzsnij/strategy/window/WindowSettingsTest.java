package com.pidluzsnij.strategy.window;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WindowSettingsTest {

    @Test
    void initialWindowIsTitledMyStrategyAndFullscreen() {
        WindowSettings settings = WindowSettings.initial();

        assertEquals("My strategy", settings.title());
        assertTrue(settings.fullscreen());
    }
}
