package com.pidluzsnij.strategy.ui;

import com.pidluzsnij.strategy.config.persistence.ConfigurationLocation;
import com.pidluzsnij.strategy.ui.definition.UiDefinitionParser;
import com.pidluzsnij.strategy.ui.resource.UiResourcePath;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * What UI-foundation initialization uses: the class loader holding the bundled {@code ui} resources, the
 * per-user configuration location under whose {@code strategy/ui} directory external overrides live, the UI
 * definitions that must load during startup and the semantic behavior identifiers application code supports.
 * Application code supplies the production values; automated tests supply isolated values.
 */
public record UiStartupConfiguration(ClassLoader bundledResources, ConfigurationLocation location,
                                     List<UiResourcePath> requiredDefinitions, Set<String> supportedBehaviors) {

    public UiStartupConfiguration {
        Objects.requireNonNull(bundledResources, "bundledResources");
        Objects.requireNonNull(location, "location");
        requiredDefinitions = List.copyOf(requiredDefinitions);
        supportedBehaviors = Set.copyOf(supportedBehaviors);
        for (String behavior : supportedBehaviors) {
            if (!UiDefinitionParser.isWellFormedBehavior(behavior)) {
                throw new IllegalArgumentException("supported behavior identifiers must be well-formed");
            }
        }
    }
}
