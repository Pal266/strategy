package com.pidluzsnij.strategy.window;

import com.pidluzsnij.strategy.config.ApplicationSettings;
import com.pidluzsnij.strategy.config.VideoSettings;
import com.pidluzsnij.strategy.localization.Localization;

/** Window title and startup mode. */
public record WindowSettings(String title, boolean fullscreen) {

    /** Localization key of the application window title. */
    public static final String TITLE_KEY = "application.window.title";

    /**
     * @return window settings titled with the text that {@code localization} returns for {@value #TITLE_KEY},
     * unchanged, using the effective {@code video.fullscreen} setting
     */
    public static WindowSettings from(ApplicationSettings settings, Localization localization) {
        return new WindowSettings(localization.text(TITLE_KEY), settings.get(VideoSettings.FULLSCREEN));
    }
}
