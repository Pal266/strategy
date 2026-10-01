package com.pidluzsnij.strategy.localization;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.file.NoSuchFileException;
import java.util.Objects;
import java.util.Optional;

/**
 * Startup initialization of localization: loads and validates the language metadata, selects the
 * configured language by exact identifier, falls back to English once when the configured language
 * is unavailable, and loads only the selected file (and English when it is needed as the fallback).
 * <p>
 * Every outcome is logged here, once. Diagnostics carry resource names, validated identifiers, counts
 * and line numbers, but never resource content, translation values or unvalidated identifiers.
 */
public final class LocalizationInitializer {

    /** Identifier of the English entry, the only fallback language. */
    public static final String ENGLISH = "en";

    private static final String STAGE_METADATA = "load language metadata";
    private static final String STAGE_FILE = "load localization file";
    private static final String STAGE_FALLBACK = "load English fallback localization file";

    private final Logger log = LoggerFactory.getLogger(LocalizationInitializer.class);
    private final LocalizationResources resources;

    public LocalizationInitializer(LocalizationResources resources) {
        this.resources = Objects.requireNonNull(resources, "resources");
    }

    /**
     * @param configuredLanguage the effective configured language identifier, already validated by configuration
     * @return the initialized localization, or empty when initialization failed fatally
     */
    public Optional<Localization> initialize(String configuredLanguage) {
        Objects.requireNonNull(configuredLanguage, "configuredLanguage");
        Progress progress = new Progress();
        try {
            return select(configuredLanguage, progress);
        } catch (LocalizationFailure failure) {
            return fail(progress, failure);
        } catch (RuntimeException | LinkageError e) {
            // Any unexpected failure in any stage is still recorded once, here, before startup ends.
            return fail(progress, new LocalizationFailure("unexpected failure", SanitizedException.of(e)));
        }
    }

    /** The stage in progress and its resource, for the failure diagnostic. */
    private static final class Progress {
        private String stage;
        private String resource;

        void enter(String stage, String resource) {
            this.stage = stage;
            this.resource = resource;
        }
    }

    private Optional<Localization> select(String configuredLanguage, Progress progress) throws LocalizationFailure {
        String metadataResource = resources.resourceName(LocalizationResources.METADATA_FILE);
        progress.enter(STAGE_METADATA, metadataResource);
        LanguageMetadata metadata = loadMetadata();
        log.debug("Language metadata loaded from {}: {} language entries", metadataResource,
                metadata.entries().size());

        Optional<LanguageMetadata.Entry> configured = metadata.find(configuredLanguage);
        LanguageMetadata.Entry english = metadata.find(ENGLISH).orElseThrow();
        String englishResource = resources.resourceName(english.localizationFile());

        if (configured.isPresent() && configured.get().language().identifier().equals(ENGLISH)) {
            progress.enter(STAGE_FILE, englishResource);
            return succeed(metadata, configuredLanguage, ENGLISH, false, loadFile(english));
        }

        String unavailableReason;
        if (configured.isPresent()) {
            LanguageMetadata.Entry entry = configured.get();
            progress.enter(STAGE_FILE, resources.resourceName(entry.localizationFile()));
            try {
                TranslationFile file = loadFile(entry);
                return succeed(metadata, configuredLanguage, entry.language().identifier(), false, file);
            } catch (LocalizationFailure failure) {
                unavailableReason = "configured language '" + entry.language().identifier() + "' is unavailable: "
                        + failure.reason();
            }
        } else {
            // The configured identifier is not echoed: it matches no validated metadata entry.
            unavailableReason = "the configured language identifier matches no language metadata entry";
        }

        log.warn("Configured language is unavailable ({}); falling back to language '{}' (resource: {})",
                unavailableReason, ENGLISH, englishResource);
        progress.enter(STAGE_FALLBACK, englishResource);
        return succeed(metadata, configuredLanguage, ENGLISH, true, loadFile(english));
    }

    private LanguageMetadata loadMetadata() throws LocalizationFailure {
        String text = read(LocalizationResources.METADATA_FILE);
        try {
            return MetadataParser.parse(text);
        } catch (MalformedResourceException e) {
            throw new LocalizationFailure(resources.resourceName(LocalizationResources.METADATA_FILE)
                    + " is malformed (" + e.getMessage() + ")", null);
        }
    }

    private TranslationFile loadFile(LanguageMetadata.Entry entry) throws LocalizationFailure {
        String fileName = entry.localizationFile();
        TranslationFile file;
        try {
            file = TranslationFile.parse(read(fileName));
        } catch (MalformedResourceException e) {
            throw new LocalizationFailure(resources.resourceName(fileName)
                    + " is malformed (" + e.getMessage() + ")", null);
        }
        log.debug("Localization file loaded for language '{}' from {}: {} keys", entry.language().identifier(),
                resources.resourceName(fileName), file.keyCount());
        return file;
    }

    /** Reads and strictly decodes one resource. */
    private String read(String fileName) throws LocalizationFailure {
        String resource = resources.resourceName(fileName);
        byte[] bytes;
        try {
            bytes = resources.read(fileName);
        } catch (NoSuchFileException e) {
            throw new LocalizationFailure(resource + " is missing", null);
        } catch (IOException | RuntimeException e) {
            throw new LocalizationFailure(resource + " could not be read", SanitizedException.of(e));
        }
        if (bytes == null) {
            throw new LocalizationFailure(resource + " could not be read", null);
        }
        try {
            return Utf8.decode(bytes);
        } catch (CharacterCodingException e) {
            throw new LocalizationFailure(resource + " is not valid UTF-8", null);
        }
    }

    private Optional<Localization> succeed(LanguageMetadata metadata, String configuredLanguage,
                                           String effectiveLanguage, boolean fallbackUsed, TranslationFile file) {
        Localization localization = new Localization(metadata.languages(), configuredLanguage, effectiveLanguage,
                fallbackUsed, file.usableValues());
        log.info("Localization initialized: effective language '{}', English fallback used: {}",
                effectiveLanguage, fallbackUsed ? "yes" : "no");
        return Optional.of(localization);
    }

    private Optional<Localization> fail(Progress progress, LocalizationFailure failure) {
        log.error("Localization initialization failed: could not {} (resource: {}): {}",
                progress.stage, progress.resource, failure.reason(), failure);
        return Optional.empty();
    }
}
