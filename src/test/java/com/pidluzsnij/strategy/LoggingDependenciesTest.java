package com.pidluzsnij.strategy;

import ch.qos.logback.classic.LoggerContext;
import com.pidluzsnij.strategy.logging.LoggingMode;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.SLF4JServiceProvider;

import java.lang.reflect.Method;
import java.util.List;
import java.util.ServiceLoader;

import static com.pidluzsnij.strategy.BuildConstraintsTest.jarName;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/** Logging dependencies and scope. */
class LoggingDependenciesTest {

    @Test
    void loggingDependenciesResolveAtSpecifiedVersions() throws Exception {
        assertEquals("slf4j-api-2.0.20.jar", jarName(org.slf4j.Logger.class));
        assertEquals("logback-classic-1.6.4.jar", jarName(LoggerContext.class));
        assertEquals("logback-core-1.6.4.jar", jarName(ch.qos.logback.core.Appender.class));
        assertEquals("directories-26.jar", jarName(dev.dirs.BaseDirectories.class));
    }

    @Test
    void logbackIsTheSoleSlf4jProvider() {
        List<String> providers = ServiceLoader.load(SLF4JServiceProvider.class).stream()
                .map(provider -> provider.type().getName())
                .toList();

        assertEquals(List.of("ch.qos.logback.classic.spi.LogbackServiceProvider"), providers);
        assertInstanceOf(LoggerContext.class, LoggerFactory.getILoggerFactory());
    }

    @Test
    void normalStartupHasNoUserFacingModeControl() throws Exception {
        Method factory = ApplicationLauncher.class.getMethod("forNormalStartup");

        assertEquals(0, factory.getParameterCount(), "normal startup must not accept a mode selection");
        assertEquals(LoggingMode.DEFAULT, ApplicationLauncher.forNormalStartup().mode());
        assertEquals(LoggingMode.DEFAULT, ApplicationLauncher.NORMAL_STARTUP_MODE);
    }
}
