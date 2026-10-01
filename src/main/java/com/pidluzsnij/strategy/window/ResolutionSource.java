package com.pidluzsnij.strategy.window;

/** Where the effective startup resolution came from. */
public enum ResolutionSource {

    /** Automatic selection used the starting monitor's current video mode. */
    MONITOR_VIDEO_MODE("monitor video mode"),
    /** Automatic selection could not determine the monitor resolution and used {@link Resolution#FALLBACK}. */
    AUTOMATIC_FALLBACK("automatic fallback"),
    /** The configured explicit dimensions. */
    EXPLICIT_CONFIGURATION("explicit configuration");

    private final String description;

    ResolutionSource(String description) {
        this.description = description;
    }

    /** @return the source's name, used in diagnostics */
    @Override
    public String toString() {
        return description;
    }
}
