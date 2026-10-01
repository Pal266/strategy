package com.pidluzsnij.strategy.window;

/** Window title and startup mode. */
public record WindowSettings(String title, boolean fullscreen) {

    public static final String TITLE = "My strategy";

    /** The application's initial window settings: titled {@value #TITLE}, fullscreen. */
    public static WindowSettings initial() {
        return new WindowSettings(TITLE, true);
    }
}
