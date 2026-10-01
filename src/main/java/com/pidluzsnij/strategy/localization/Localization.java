package com.pidluzsnij.strategy.localization;

import com.pidluzsnij.strategy.text.UnicodeWhiteSpace;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Initialized localization: the available languages and lookup of localized text in the effective
 * language. Entries are held in memory, so lookups never read resources or configuration; changes to
 * resources or the configured language take effect on the next startup.
 */
public final class Localization {

    private final List<Language> languages;
    private final String configuredLanguage;
    private final String effectiveLanguage;
    private final boolean fallbackUsed;
    private final Map<String, String> values;

    Localization(List<Language> languages, String configuredLanguage, String effectiveLanguage,
                 boolean fallbackUsed, Map<String, String> values) {
        this.languages = List.copyOf(languages);
        this.configuredLanguage = Objects.requireNonNull(configuredLanguage, "configuredLanguage");
        this.effectiveLanguage = Objects.requireNonNull(effectiveLanguage, "effectiveLanguage");
        this.fallbackUsed = fallbackUsed;
        this.values = Map.copyOf(values);
    }

    /** @return the available languages in metadata order */
    public List<Language> languages() {
        return languages;
    }

    /** @return the language identifier configured in the application settings */
    public String configuredLanguage() {
        return configuredLanguage;
    }

    /** @return the identifier of the language whose entries are used for lookup */
    public String effectiveLanguage() {
        return effectiveLanguage;
    }

    /** @return whether English was selected because the configured language was unavailable */
    public boolean fallbackUsed() {
        return fallbackUsed;
    }

    /**
     * Looks up {@code key} in the effective language. There is no per-key fallback to another language.
     *
     * @param key the exact key; it is not trimmed
     * @return the key's value when present with a nonempty, non-whitespace-only value; otherwise {@code key}
     * @throws NullPointerException     when {@code key} is {@code null}
     * @throws IllegalArgumentException when {@code key} is empty or whitespace-only
     */
    public String text(String key) {
        Objects.requireNonNull(key, "key");
        if (UnicodeWhiteSpace.isBlank(key)) {
            throw new IllegalArgumentException("localization key must not be empty or whitespace-only");
        }
        String value = values.get(key);
        return value == null ? key : value;
    }

    /** Deliberately omits values so that translations never reach diagnostics. */
    @Override
    public String toString() {
        return "Localization[effective language " + effectiveLanguage + ", " + values.size() + " values]";
    }
}
