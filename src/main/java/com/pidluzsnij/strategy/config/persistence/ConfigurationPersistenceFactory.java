package com.pidluzsnij.strategy.config.persistence;

import com.pidluzsnij.strategy.config.SettingsSchema;

import java.nio.file.Path;

/** Creates the persistence for a schema, rooted at a resolved configuration base directory. */
@FunctionalInterface
public interface ConfigurationPersistenceFactory {

    ConfigurationPersistence create(SettingsSchema schema, Path configDirectory);
}
