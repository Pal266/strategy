package com.pidluzsnij.strategy.testsupport;

import com.pidluzsnij.strategy.ApplicationLauncher;
import com.pidluzsnij.strategy.config.ApplicationSettings;
import com.pidluzsnij.strategy.config.SettingsSchema;
import com.pidluzsnij.strategy.config.persistence.ConfigurationLocation;
import com.pidluzsnij.strategy.config.persistence.StorageOperations;
import com.pidluzsnij.strategy.config.persistence.toml.TomlConfigurationPersistence;
import com.pidluzsnij.strategy.localization.LocalizationResources;
import com.pidluzsnij.strategy.logging.FileOperations;
import com.pidluzsnij.strategy.logging.LogLocation;
import com.pidluzsnij.strategy.logging.LoggingMode;
import com.pidluzsnij.strategy.ui.UiStartupConfiguration;
import com.pidluzsnij.strategy.window.RuntimeEnvironment;
import com.pidluzsnij.strategy.window.WindowSystem;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Runs the launcher against an isolated per-user configuration directory, used for both
 * the log file and the configuration file.
 */
public final class LogHarness {

    public static final RuntimeEnvironment KNOWN_RUNTIME =
            new RuntimeEnvironment("21.0.42-test", "TestOS", "9.8.7", "test-arch", "3.4.3-test");

    /** Start of a record: timestamp with timezone, level, thread, logger. */
    public static final Pattern RECORD_START = Pattern.compile(
            "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}(Z|[+-]\\d{2}:\\d{2}) "
                    + "(TRACE|DEBUG|INFO |WARN |ERROR) \\[[^\\]]+] \\S+ - .*");

    public final Path configDirectory;
    public final ByteArrayOutputStream stderrBytes = new ByteArrayOutputStream();
    public final PrintStream stderr = new PrintStream(stderrBytes, true, StandardCharsets.UTF_8);
    public final AtomicBoolean infrastructureStarted = new AtomicBoolean();

    /** Settings recognized by the launched application. */
    public SettingsSchema settingsSchema = ApplicationSettings.SCHEMA;
    /** Storage operations used by configuration persistence. */
    public StorageOperations storage = StorageOperations.SYSTEM;
    /** Configuration location; by default the same isolated directory as the log. */
    public ConfigurationLocation configurationLocation;
    /** Localization resources; by default the application's bundled, read-only resources. */
    public LocalizationResources localizationResources = LocalizationResources.bundled();
    /** UI-foundation configuration; by default the production configuration at {@link #configurationLocation}. */
    public UiStartupConfiguration uiConfiguration;

    public LogHarness(Path tempDirectory) {
        this.configDirectory = tempDirectory.resolve("config");
        this.configurationLocation = () -> configDirectory;
    }

    public Path logFile() {
        return configDirectory.resolve("strategy").resolve("log.log");
    }

    public Path settingsFile() {
        return configDirectory.resolve("strategy").resolve("application-settings.toml");
    }

    public LogLocation location() {
        return () -> configDirectory;
    }

    public int launch(LoggingMode mode, WindowSystem windowSystem) {
        return launch(mode, FileOperations.SYSTEM, location(), windowSystem);
    }

    public int launch(LoggingMode mode, FileOperations fileOperations, LogLocation location,
                      WindowSystem windowSystem) {
        Supplier<WindowSystem> factory = () -> {
            infrastructureStarted.set(true);
            return windowSystem;
        };
        Supplier<RuntimeEnvironment> runtime = () -> {
            infrastructureStarted.set(true);
            return KNOWN_RUNTIME;
        };
        StorageOperations configurationStorage = storage;
        return new ApplicationLauncher(mode, location, fileOperations, stderr, settingsSchema, configurationLocation,
                (schema, directory) -> new TomlConfigurationPersistence(schema, directory, configurationStorage),
                localizationResources, factory, runtime,
                uiConfiguration != null ? uiConfiguration : UiStartupConfiguration.production(configurationLocation))
                .launch();
    }

    public String stderr() {
        return stderrBytes.toString(StandardCharsets.UTF_8);
    }

    /** Reads the log strictly as UTF-8. */
    public String log() {
        return readUtf8(logFile());
    }

    /** Records in the log, each possibly spanning several lines. */
    public List<String> records() {
        return splitRecords(log());
    }

    public static String readUtf8(Path file) {
        try {
            byte[] bytes = Files.readAllBytes(file);
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            throw new AssertionError("log is not valid UTF-8", e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static List<String> splitRecords(String content) {
        List<String> records = new ArrayList<>();
        StringBuilder current = null;
        for (String line : content.split("\\R")) {
            if (RECORD_START.matcher(line).matches()) {
                if (current != null) {
                    records.add(current.toString());
                }
                current = new StringBuilder(line);
            } else if (current != null) {
                current.append('\n').append(line);
            } else if (!line.isEmpty()) {
                throw new AssertionError("log does not start with a record: " + line);
            }
        }
        if (current != null) {
            records.add(current.toString());
        }
        return records;
    }

    public static int count(String haystack, String needle) {
        int count = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + needle.length())) {
            count++;
        }
        return count;
    }
}
