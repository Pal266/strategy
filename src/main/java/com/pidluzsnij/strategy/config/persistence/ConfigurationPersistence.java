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
     * Loads and normalizes the persisted configuration.
     *
     * @return {@link LoadResult.Loaded}, {@link LoadResult.NotFound} or {@link LoadResult.Failed}; never throws
     *         for storage or format problems
     */
    LoadResult load();

    /**
     * Persists the complete snapshot, creating required directories, and atomically
     * replaces any existing configuration. A failed save leaves an existing file intact.
     */
    void save(ApplicationSettings settings) throws ConfigurationPersistenceException;
}
