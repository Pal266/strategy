package com.pidluzsnij.strategy.config.persistence;

import dev.dirs.BaseDirectories;

import java.nio.file.Path;

/** Production location: the operating system's per-user configuration directory reported by directories-jvm. */
public final class DirectoriesConfigurationLocation implements ConfigurationLocation {

    @Override
    public Path configDirectory() {
        String configDir = BaseDirectories.get().configDir;
        if (configDir == null || configDir.isBlank()) {
            throw new IllegalStateException("the operating system did not report a per-user configuration directory");
        }
        Path path = Path.of(configDir);
        if (!path.isAbsolute()) {
            throw new IllegalStateException("the reported per-user configuration directory is not absolute");
        }
        return path;
    }
}
