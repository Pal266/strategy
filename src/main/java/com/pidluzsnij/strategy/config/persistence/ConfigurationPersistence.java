package com.pidluzsnij.strategy.config.persistence;

import com.pidluzsnij.strategy.config.ApplicationSettings;

import java.nio.file.Path;

/**
 * Loads and saves application settings independently of their persistence format.
 * Implementations understand storage only; defaults and validation belong to
 * {@link ApplicationSettings}, and startup policy belongs to the application.
 */
public interface ConfigurationPersistence {

    /** @return the absolute path of the persisted configuration file */
    Path file();

    /**
     * Loads the persisted configuration and normalizes it with {@link ApplicationSettings#normalize}.
     * <p>
     * Storage and format problems are reported as {@link LoadResult.Failed}, never thrown. Programming
     * errors, such as a setting validation rule that throws, propagate as unchecked exceptions.
     *
     * @return {@link LoadResult.Loaded}, {@link LoadResult.NotFound} or {@link LoadResult.Failed}
     */
    LoadResult load();

    /**
     * Persists the complete snapshot, creating required directories, and atomically
     * replaces any existing configuration. A failed save leaves an existing file intact.
     *
     * @throws IllegalArgumentException when {@code settings} use a different schema than this persistence
     */
    void save(ApplicationSettings settings) throws ConfigurationPersistenceException;
}
