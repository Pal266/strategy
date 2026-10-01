package com.pidluzsnij.strategy.logging;

import java.nio.file.Path;

/** Raised when diagnostic logging cannot be initialized; startup must abort. */
public final class LoggingInitializationException extends Exception {

    /** Initialization failure categories. */
    public enum Reason {
        LOCATION_UNAVAILABLE,
        DIRECTORY_CREATION_FAILED,
        FILE_OPEN_FAILED,
        FILE_IN_USE,
        OWNERSHIP_ACQUISITION_FAILED,
        TRUNCATION_FAILED,
        CONFIGURATION_FAILED
    }

    private final Reason reason;
    private final Path attemptedPath;

    LoggingInitializationException(Reason reason, Path attemptedPath, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
        this.attemptedPath = attemptedPath;
    }

    public Reason reason() {
        return reason;
    }

    /** @return the attempted absolute log-file path, or {@code null} when unknown */
    public Path attemptedPath() {
        return attemptedPath;
    }
}
