package com.pidluzsnij.strategy.ui;

/** One or more UI resources could not be released; each failure is attached as a suppressed exception. */
public final class UiCleanupException extends RuntimeException {

    public UiCleanupException(String message) {
        super(message);
    }
}
