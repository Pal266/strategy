package com.pidluzsnij.strategy.localization;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Stand-in for an exception whose message might contain resource content: it keeps only the
 * original type name and stack trace, and sanitizes causes and suppressed exceptions the same way.
 */
final class SanitizedException extends Exception {

    private SanitizedException(Throwable original, SanitizedException cause) {
        super(original.getClass().getName() + " (message omitted)", cause);
        setStackTrace(original.getStackTrace());
    }

    /** @return a sanitized copy of {@code original}, or {@code null} when it is {@code null} */
    static SanitizedException of(Throwable original) {
        return original == null ? null : copy(original, Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    private static SanitizedException copy(Throwable original, Set<Throwable> visited) {
        visited.add(original);
        Throwable cause = original.getCause();
        SanitizedException sanitizedCause = cause == null || visited.contains(cause) ? null : copy(cause, visited);
        SanitizedException copy = new SanitizedException(original, sanitizedCause);
        for (Throwable suppressed : original.getSuppressed()) {
            if (!visited.contains(suppressed)) {
                copy.addSuppressed(copy(suppressed, visited));
            }
        }
        return copy;
    }
}
