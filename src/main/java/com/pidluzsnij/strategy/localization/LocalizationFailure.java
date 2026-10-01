package com.pidluzsnij.strategy.localization;

/**
 * A localization resource that could not be used. The message contains only safe diagnostic
 * context (resource names, line numbers, fixed descriptions); any cause has been sanitized.
 */
final class LocalizationFailure extends Exception {

    private final String reason;

    LocalizationFailure(String reason, Throwable sanitizedCause) {
        super(reason, sanitizedCause);
        this.reason = reason;
    }

    /** @return the safe description of why the resource could not be used */
    String reason() {
        return reason;
    }
}
