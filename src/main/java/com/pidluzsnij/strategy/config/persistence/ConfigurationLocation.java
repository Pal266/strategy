package com.pidluzsnij.strategy.config.persistence;

import java.nio.file.Path;

/**
 * Supplies the per-user configuration base directory under which the application's
 * {@code strategy} configuration directory lives. Tests supply isolated directories.
 */
@FunctionalInterface
public interface ConfigurationLocation {

    /**
     * @return the configuration base directory
     * @throws Exception when the directory cannot be determined
     */
    Path configDirectory() throws Exception;
}
