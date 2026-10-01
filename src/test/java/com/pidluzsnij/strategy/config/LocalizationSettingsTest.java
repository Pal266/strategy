package com.pidluzsnij.strategy.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.pidluzsnij.strategy.config.LocalizationSettings.LANGUAGE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The production {@code localization.language} setting: default, basic validation and normalization. */
class LocalizationSettingsTest {

    private static final SettingsSchema SCHEMA = ApplicationSettings.SCHEMA;

    private static Map<String, Object> table(Object... keyValues) {
        Map<String, Object> table = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            table.put((String) keyValues[i], keyValues[i + 1]);
        }
        return table;
    }

    private static Map<String, Object> video() {
        return table("fullscreen", false, "resolution", table("width", 1600L, "height", 900L));
    }

    private static SettingsNormalization normalize(Object localization) {
        Map<String, Object> root = table("video", video());
        if (localization != null) {
            root.put("localization", localization);
        }
        return ApplicationSettings.normalize(SCHEMA, new PersistedSettings(root));
    }

    @Test
    void defaultsToEnglishAndIsPersistedInTheLocalizationSection() {
        ApplicationSettings defaults = ApplicationSettings.defaults(SCHEMA);

        assertEquals("localization.language", LANGUAGE.id());
        assertEquals("en", defaults.get(LANGUAGE));
        assertEquals(table("language", "en"), defaults.toPersisted().root().get("localization"));
        assertEquals(List.of(VideoSettings.FULLSCREEN, VideoSettings.RESOLUTION, LANGUAGE), SCHEMA.settings(),
                "localization.language is the only new production setting");
    }

    @Test
    void validIdentifiersAreKeptWithoutMembershipChecks() {
        for (String identifier : List.of("en", "uk", "EN", "unknown-language", "x y", "Čeština")) {
            SettingsNormalization normalization = normalize(table("language", identifier));
            assertFalse(normalization.changed(), identifier);
            assertEquals(identifier, normalization.settings().get(LANGUAGE));
        }
    }

    static List<Object> incompatibleValues() {
        return List.of(true, 42L, 1.5, List.of("en"), table("id", "en"));
    }

    @ParameterizedTest
    @MethodSource("incompatibleValues")
    void nonStringValuesAreIncompatibleAndNotCoerced(Object value) {
        SettingsNormalization normalization = normalize(table("language", value));

        assertEquals("en", normalization.settings().get(LANGUAGE));
        assertEquals(List.of(new InvalidSetting("localization.language", InvalidSetting.Reason.INCOMPATIBLE_TYPE,
                "string")), normalization.invalid());
        assertTrue(normalization.unknown().isEmpty());
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"", " ", "\t", " 　", " en", "en ",
            "\ten", "en ", " en"})
    void blankOrPaddedStringsFailValidation(String value) {
        SettingsNormalization normalization = normalize(table("language", value));

        assertEquals("en", normalization.settings().get(LANGUAGE));
        assertEquals(List.of(new InvalidSetting("localization.language", InvalidSetting.Reason.FAILED_VALIDATION,
                "string")), normalization.invalid());
        assertThrows(IllegalArgumentException.class, () -> ApplicationSettings.defaults(SCHEMA).with(LANGUAGE, value));
    }

    @Test
    void missingSectionOrValueIsAMissingSetting() {
        for (Object localization : java.util.Arrays.asList(null, table())) {
            SettingsNormalization normalization = normalize(localization);
            assertEquals(List.of("localization.language"), normalization.missing());
            assertTrue(normalization.invalid().isEmpty());
            assertTrue(normalization.unknown().isEmpty());
            assertEquals("en", normalization.settings().get(LANGUAGE));
        }
    }

    @Test
    void nonTableSectionIsAnInvalidLanguageAndReplacedWithDefaults() {
        for (Object localization : List.of("uk", 5L, true, List.of(), List.of("uk"))) {
            SettingsNormalization normalization = normalize(localization);

            assertEquals(List.of(new InvalidSetting("localization.language",
                    InvalidSetting.Reason.SECTION_NOT_A_TABLE, "string")), normalization.invalid());
            assertTrue(normalization.missing().isEmpty());
            assertTrue(normalization.unknown().isEmpty(), "the section is not separately reported as unknown");
            assertTrue(normalization.changed());
            Map<String, Object> persisted = normalization.settings().toPersisted().root();
            assertEquals(table("language", "en"), persisted.get("localization"));
            assertEquals(video(), persisted.get("video"), "other recognized settings are preserved");
        }
    }

    @Test
    void otherSettingsKeepTheirExistingNonTableSectionBehaviour() {
        SettingsNormalization normalization = ApplicationSettings.normalize(SCHEMA,
                new PersistedSettings(table("video", 5L, "localization", table("language", "uk"))));

        assertEquals(List.of("video"), normalization.unknown());
        assertEquals(List.of("video.fullscreen", "video.resolution"), normalization.missing());
        assertTrue(normalization.invalid().isEmpty());
    }
}
