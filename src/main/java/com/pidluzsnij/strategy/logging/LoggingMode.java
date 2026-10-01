package com.pidluzsnij.strategy.logging;

import ch.qos.logback.classic.Level;

/**
 * Diagnostic logging modes.
 * <p>
 * {@link #DEFAULT} accepts INFO, WARN and ERROR records; {@link #DEVELOPMENT}
 * additionally accepts DEBUG and TRACE records.
 */
public enum LoggingMode {
    DEFAULT("default", Level.INFO),
    DEVELOPMENT("development", Level.TRACE);

    private final String id;
    private final Level threshold;

    LoggingMode(String id, Level threshold) {
        this.id = id;
        this.threshold = threshold;
    }

    /** Name of the mode as written to diagnostics. */
    public String id() {
        return id;
    }

    Level threshold() {
        return threshold;
    }
}
