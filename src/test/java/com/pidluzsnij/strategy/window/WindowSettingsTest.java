package com.pidluzsnij.strategy.window;

import com.pidluzsnij.strategy.config.ApplicationSettings;
import com.pidluzsnij.strategy.config.VideoSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WindowSettingsTest {

    @Test
    void defaultWindowIsTitledMyStrategyAndFullscreen() {
        WindowSettings settings = WindowSettings.from(ApplicationSettings.defaults(ApplicationSettings.SCHEMA));

        assertEquals("My strategy", settings.title());
        assertTrue(settings.fullscreen());
    }

    @Test
    void windowedVideoSettingKeepsTheTitle() {
        WindowSettings settings = WindowSettings.from(ApplicationSettings.defaults(ApplicationSettings.SCHEMA)
                .with(VideoSettings.FULLSCREEN, false));

        assertEquals("My strategy", settings.title());
        assertFalse(settings.fullscreen());
    }
}
