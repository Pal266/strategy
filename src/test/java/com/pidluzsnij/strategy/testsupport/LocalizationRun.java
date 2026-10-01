package com.pidluzsnij.strategy.testsupport;

import com.pidluzsnij.strategy.localization.Localization;
import com.pidluzsnij.strategy.localization.LocalizationInitializer;
import com.pidluzsnij.strategy.localization.LocalizationResources;
import com.pidluzsnij.strategy.logging.FileOperations;
import com.pidluzsnij.strategy.logging.LoggingMode;
import com.pidluzsnij.strategy.logging.LoggingSystem;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** Runs localization initialization alone, with diagnostic logging in an isolated directory. */
public final class LocalizationRun {

    public static final String LOGGER = "] com.pidluzsnij.strategy.localization.LocalizationInitializer - ";
    public static final String SUCCESS = "Localization initialized";
    public static final String FALLBACK = "Configured language is unavailable";
    public static final String FAILURE = "Localization initialization failed";
    public static final String METADATA_LOADED = "Language metadata loaded";
    public static final String FILE_LOADED = "Localization file loaded";

    /** Result of one initialization. */
    public record Outcome(Optional<Localization> localization, List<String> records, String log, String stderr) {

        public Localization get() {
            return localization.orElseThrow(() -> new AssertionError("initialization failed: " + log));
        }

        public List<String> withLevel(String level) {
            return records.stream().filter(r -> r.contains(" " + level + " [")).toList();
        }

        public List<String> containing(String level, String text) {
            return withLevel(level).stream().filter(r -> r.contains(text)).toList();
        }

        /** Records of the localization initializer. */
        public List<String> localizationRecords() {
            return records.stream().filter(r -> r.contains(LOGGER)).toList();
        }
    }

    private LocalizationRun() {
    }

    public static Outcome run(Path directory, LoggingMode mode, LocalizationResources resources,
                              String configuredLanguage) throws Exception {
        LogHarness harness = new LogHarness(directory);
        Optional<Localization> localization;
        LoggingSystem logging = LoggingSystem.initialize(mode, harness.location(), FileOperations.SYSTEM,
                harness.stderr);
        try {
            localization = new LocalizationInitializer(resources).initialize(configuredLanguage);
        } finally {
            logging.close();
        }
        return new Outcome(localization, harness.records(), harness.log(), harness.stderr());
    }
}
