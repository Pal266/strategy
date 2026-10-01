package com.pidluzsnij.strategy.logging;

import dev.dirs.BaseDirectories;

import java.nio.file.Path;

/**
 * Resolves the per-user configuration directory with the {@code directories} library.
 * The result does not depend on configuration loading or the process working directory.
 */
public final class DirectoriesLogLocation implements LogLocation {

    @Override
    public Path configDirectory() {
        String configDir = BaseDirectories.get().configDir;
        System.out.println(configDir);
        if (configDir == null || configDir.isBlank()) {
            throw new IllegalStateException("the operating system did not report a per-user configuration directory");
        }
        Path path = Path.of(configDir);
        if (!path.isAbsolute()) {
            throw new IllegalStateException("the reported per-user configuration directory is not absolute: " + configDir);
        }
        return path;
    }
}
