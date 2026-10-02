package com.pidluzsnij.strategy.paths;

/**
 * The application's per-user directory, created under the operating system's per-user configuration base
 * directory. Configuration, logging and UI overrides share it.
 */
public final class ApplicationDirectory {

    /** Name of the application's per-user directory. */
    public static final String NAME = "strategy";

    private ApplicationDirectory() {
    }
}
