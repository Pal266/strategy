package com.pidluzsnij.strategy.window;

import com.pidluzsnij.strategy.config.ApplicationSettings;
import com.pidluzsnij.strategy.config.VideoSettings;
import com.pidluzsnij.strategy.localization.Localization;
import com.pidluzsnij.strategy.localization.LocalizationInitializer;
import com.pidluzsnij.strategy.localization.LocalizationResources;
import com.pidluzsnij.strategy.testsupport.FixtureResources;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WindowSettingsTest {

    private static Localization localization(LocalizationResources resources, String language) {
        return new LocalizationInitializer(resources).initialize(language).orElseThrow();
    }

    @Test
    void defaultWindowIsTitledMyStrategyAndFullscreen() {
        WindowSettings settings = WindowSettings.from(ApplicationSettings.defaults(ApplicationSettings.SCHEMA),
                localization(LocalizationResources.bundled(), "en"));

        assertEquals("My strategy", settings.title());
        assertTrue(settings.fullscreen());
    }

    @Test
    void windowedVideoSettingKeepsTheTitle() {
        WindowSettings settings = WindowSettings.from(ApplicationSettings.defaults(ApplicationSettings.SCHEMA)
                .with(VideoSettings.FULLSCREEN, false), localization(LocalizationResources.bundled(), "en"));

        assertEquals("My strategy", settings.title());
        assertFalse(settings.fullscreen());
    }

    @Test
    void titleIsTheLocalizationLookupOfTheTitleKey() {
        assertEquals("application.window.title", WindowSettings.TITLE_KEY);
        Localization localization = localization(new FixtureResources()
                .put("czech.properties", "application.window.title=  Titulek ž =x \t\n"), "cs");

        WindowSettings settings = WindowSettings.from(ApplicationSettings.defaults(ApplicationSettings.SCHEMA),
                localization);

        assertEquals(localization.text(WindowSettings.TITLE_KEY), settings.title());
        assertEquals("  Titulek ž =x \t", settings.title());
    }

    @Test
    void absentTitleKeyBecomesTheTitle() {
        WindowSettings settings = WindowSettings.from(ApplicationSettings.defaults(ApplicationSettings.SCHEMA),
                localization(new FixtureResources(), "hu"));

        assertEquals("application.window.title", settings.title());
    }
}
