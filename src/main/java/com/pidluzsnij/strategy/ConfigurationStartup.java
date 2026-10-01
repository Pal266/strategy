package com.pidluzsnij.strategy;

import com.pidluzsnij.strategy.config.ApplicationSettings;
import com.pidluzsnij.strategy.config.InvalidSetting;
import com.pidluzsnij.strategy.config.SettingsNormalization;
import com.pidluzsnij.strategy.config.SettingsSchema;
import com.pidluzsnij.strategy.config.persistence.ConfigurationLocation;
import com.pidluzsnij.strategy.config.persistence.ConfigurationPersistence;
import com.pidluzsnij.strategy.config.persistence.ConfigurationPersistenceException;
import com.pidluzsnij.strategy.config.persistence.ConfigurationPersistenceFactory;
import com.pidluzsnij.strategy.config.persistence.LoadResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Startup policy for configuration: a missing configuration is created with defaults,
 * a changed normalization is persisted, and every persistence failure is fatal.
 * <p>
 * Each failure is logged exactly once, here; diagnostics never include configuration values.
 */
final class ConfigurationStartup {

    private static final String UNAVAILABLE = "unavailable";

    private final Logger log = LoggerFactory.getLogger(ConfigurationStartup.class);
    private final SettingsSchema schema;
    private final ConfigurationLocation location;
    private final ConfigurationPersistenceFactory persistenceFactory;

    ConfigurationStartup(SettingsSchema schema, ConfigurationLocation location,
                         ConfigurationPersistenceFactory persistenceFactory) {
        this.schema = schema;
        this.location = location;
        this.persistenceFactory = persistenceFactory;
    }

    /** @return the complete effective settings, or empty when startup must not continue */
    Optional<ApplicationSettings> initialize() {
        Path configDirectory;
        try {
            configDirectory = location.configDirectory();
            if (configDirectory == null) {
                throw new IllegalStateException("no configuration directory was reported");
            }
        } catch (Exception | LinkageError e) {
            return fail("resolve configuration location", null, e);
        }

        ConfigurationPersistence persistence;
        try {
            persistence = persistenceFactory.create(schema, configDirectory);
        } catch (RuntimeException | LinkageError e) {
            // Also covers a configuration library missing at runtime, which surfaces on first use.
            return fail("create configuration persistence", null, e);
        }
        Path file = persistence.file();
        log.debug("Configuration location resolved: {}", file);

        try {
            LoadResult result = persistence.load();
            ApplicationSettings settings;
            if (result instanceof LoadResult.NotFound) {
                settings = createDefaults(persistence, file);
            } else if (result instanceof LoadResult.Loaded loaded) {
                settings = applyNormalization(persistence, loaded);
            } else if (result instanceof LoadResult.Failed failed) {
                return fail(failed.failure());
            } else {
                throw new IllegalStateException("unexpected load result " + result.getClass().getName());
            }
            log.info("Configuration initialized; effective settings are ready for subsequent startup");
            return Optional.of(settings);
        } catch (ConfigurationPersistenceException e) {
            return fail(e);
        } catch (RuntimeException | LinkageError e) {
            return fail("initialize configuration", file, e);
        }
    }

    private ApplicationSettings createDefaults(ConfigurationPersistence persistence, Path file)
            throws ConfigurationPersistenceException {
        ApplicationSettings defaults = ApplicationSettings.defaults(schema);
        persistence.save(defaults);
        log.debug("Configuration snapshot saved to {} (reason: first-run creation)", file);
        log.info("Configuration file did not exist; created it with default settings at {}", file);
        return defaults;
    }

    private ApplicationSettings applyNormalization(ConfigurationPersistence persistence, LoadResult.Loaded loaded)
            throws ConfigurationPersistenceException {
        Path file = loaded.file();
        SettingsNormalization normalization = loaded.normalization();
        log.debug("Existing configuration loaded from {}", file);
        for (String id : normalization.missing()) {
            log.debug("Configuration setting '{}' is missing; using its default", printable(id));
        }
        for (InvalidSetting invalid : normalization.invalid()) {
            log.warn("Configuration setting '{}' has an invalid value ({}); using its default",
                    printable(invalid.id()), describe(invalid));
        }
        for (String id : normalization.unknown()) {
            log.debug("Unknown configuration setting '{}' discarded", printable(id));
        }

        if (!normalization.changed()) {
            log.debug("Configuration at {} requires no rewrite; normalization made no changes", file);
            return normalization.settings();
        }
        log.debug("Configuration normalization changed the configuration: {} missing value(s) defaulted, "
                        + "{} invalid value(s) defaulted, {} unknown setting(s) discarded",
                normalization.missing().size(), normalization.invalid().size(), normalization.unknown().size());
        persistence.save(normalization.settings());
        log.debug("Configuration snapshot saved to {} (reason: normalization)", file);
        return normalization.settings();
    }

    /**
     * Escapes backslashes and control or line-separator characters in a setting identifier, so that an
     * identifier read from the file (for example a quoted TOML key) cannot forge log records.
     */
    static String printable(String id) {
        StringBuilder result = new StringBuilder(id.length());
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            if (c == '\\') {
                result.append("\\\\");
            } else if (Character.isISOControl(c) || c == '\u2028' || c == '\u2029') {
                result.append(String.format("\\u%04X", (int) c));
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    private static String describe(InvalidSetting invalid) {
        return switch (invalid.reason()) {
            case INCOMPATIBLE_TYPE -> "incompatible type; expected " + invalid.expectedType();
            case FAILED_VALIDATION -> "failed validation";
        };
    }

    private Optional<ApplicationSettings> fail(ConfigurationPersistenceException failure) {
        return fail(failure.operation().description(), failure.file(), failure);
    }

    private Optional<ApplicationSettings> fail(String operation, Path file, Throwable cause) {
        log.error("Configuration initialization failed: could not {} (configuration file: {})",
                operation, file == null ? UNAVAILABLE : file, cause);
        return Optional.empty();
    }
}
