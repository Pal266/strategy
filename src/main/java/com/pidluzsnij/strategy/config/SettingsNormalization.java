package com.pidluzsnij.strategy.config;

import java.util.List;
import java.util.Objects;

/**
 * Result of normalizing persisted configuration: the complete effective settings and
 * every change made relative to what was persisted.
 *
 * @param settings complete effective settings
 * @param missing  identifiers of recognized settings that were absent and received their default
 * @param invalid  recognized settings whose invalid value was replaced with their default
 * @param unknown  identifiers of unknown persisted settings that were discarded
 */
public record SettingsNormalization(ApplicationSettings settings, List<String> missing,
                                    List<InvalidSetting> invalid, List<String> unknown) {

    public SettingsNormalization {
        Objects.requireNonNull(settings, "settings");
        missing = List.copyOf(missing);
        invalid = List.copyOf(invalid);
        unknown = List.copyOf(unknown);
    }

    /** @return whether the effective settings differ from what was persisted */
    public boolean changed() {
        return !missing.isEmpty() || !invalid.isEmpty() || !unknown.isEmpty();
    }
}
