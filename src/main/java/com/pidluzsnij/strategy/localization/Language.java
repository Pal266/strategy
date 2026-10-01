package com.pidluzsnij.strategy.localization;

import java.util.Objects;

/**
 * A language available for localization, as described by the language metadata.
 *
 * @param identifier  the language identifier, matched exactly and case-sensitively
 * @param displayName the language's name as it is displayed to users
 */
public record Language(String identifier, String displayName) {

    public Language {
        Objects.requireNonNull(identifier, "identifier");
        Objects.requireNonNull(displayName, "displayName");
    }
}
