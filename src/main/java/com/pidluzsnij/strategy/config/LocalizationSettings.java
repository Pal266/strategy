package com.pidluzsnij.strategy.config;

import com.pidluzsnij.strategy.text.UnicodeWhiteSpace;

/** Settings of the {@code localization} section of the application settings. */
public final class LocalizationSettings {

    /** Identifier of the language selected by default and used as the localization fallback. */
    public static final String DEFAULT_LANGUAGE = "en";

    /**
     * Configured language identifier: a nonempty string that is not whitespace-only and has no leading or
     * trailing whitespace. Whether a language with this identifier exists is not a configuration concern.
     * A {@code localization} section that is not a table makes this setting invalid.
     */
    public static final Setting<String> LANGUAGE =
            Setting.of("localization.language", SettingType.STRING, DEFAULT_LANGUAGE,
                    LocalizationSettings::isValidLanguage).invalidWhenSectionIsNotATable();

    private LocalizationSettings() {
    }

    private static boolean isValidLanguage(String value) {
        return !UnicodeWhiteSpace.isBlank(value) && !UnicodeWhiteSpace.hasSurroundingWhiteSpace(value);
    }
}
