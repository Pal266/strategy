package com.pidluzsnij.strategy.logging;

import java.nio.file.Path;

/**
 * Supplies the operating system's conventional per-user configuration directory,
 * under which the application's {@code strategy/log.log} file lives.
 */
@FunctionalInterface
public interface LogLocation {

    /**
     * @return the per-user configuration directory
     * @throws Exception when the directory cannot be determined
     */
    Path configDirectory() throws Exception;
}
