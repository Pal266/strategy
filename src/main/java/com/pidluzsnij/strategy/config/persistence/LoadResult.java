package com.pidluzsnij.strategy.config.persistence;

import com.pidluzsnij.strategy.config.ApplicationSettings;
import com.pidluzsnij.strategy.config.SettingsNormalization;

import java.nio.file.Path;
import java.util.Objects;

/** Outcome of loading persisted configuration. */
public sealed interface LoadResult {

    /** The configuration was read and parsed; its values were normalized. */
    record Loaded(Path file, SettingsNormalization normalization) implements LoadResult {
        public Loaded {
            Objects.requireNonNull(file, "file");
            Objects.requireNonNull(normalization, "normalization");
        }

        /** @return the complete effective settings */
        public ApplicationSettings settings() {
            return normalization.settings();
        }
    }

    /** No configuration has been persisted yet. */
    record NotFound(Path file) implements LoadResult {
        public NotFound {
            Objects.requireNonNull(file, "file");
        }
    }

    /** The configuration exists but could not be read or parsed. */
    record Failed(ConfigurationPersistenceException failure) implements LoadResult {
        public Failed {
            Objects.requireNonNull(failure, "failure");
        }
    }
}
