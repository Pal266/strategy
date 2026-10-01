package com.pidluzsnij.strategy.window;

import com.pidluzsnij.strategy.config.ApplicationSettings;
import com.pidluzsnij.strategy.config.VideoSettings;

/** Window title and startup mode. */
public record WindowSettings(String title, boolean fullscreen) {

    public static final String TITLE = "My strategy";

    /** @return window settings titled {@value #TITLE} using the effective {@code video.fullscreen} setting */
    public static WindowSettings from(ApplicationSettings settings) {
        return new WindowSettings(TITLE, settings.get(VideoSettings.FULLSCREEN));
    }
}
