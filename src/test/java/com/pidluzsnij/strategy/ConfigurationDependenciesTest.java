package com.pidluzsnij.strategy;

import org.junit.jupiter.api.Test;
import org.slf4j.spi.SLF4JServiceProvider;

import java.util.List;
import java.util.ServiceLoader;

import static com.pidluzsnij.strategy.BuildConstraintsTest.jarName;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Configuration dependencies and their versions. */
class ConfigurationDependenciesTest {

    @Test
    void nightConfigTomlResolvesAt390() throws Exception {
        assertEquals("toml-3.9.0.jar", jarName(com.electronwill.nightconfig.toml.TomlFormat.class));
        assertEquals("core-3.9.0.jar", jarName(com.electronwill.nightconfig.core.Config.class));
    }

    @Test
    void directoriesAndLoggingReuseTheExistingDependencies() throws Exception {
        assertEquals("directories-26.jar", jarName(dev.dirs.BaseDirectories.class));
        assertEquals("slf4j-api-2.0.20.jar", jarName(org.slf4j.Logger.class));
        List<String> providers = ServiceLoader.load(SLF4JServiceProvider.class).stream()
                .map(provider -> provider.type().getName())
                .toList();
        assertEquals(List.of("ch.qos.logback.classic.spi.LogbackServiceProvider"), providers);
    }
}
