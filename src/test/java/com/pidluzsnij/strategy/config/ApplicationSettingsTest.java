package com.pidluzsnij.strategy.config;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.pidluzsnij.strategy.testsupport.TestSettings.COUNT;
import static com.pidluzsnij.strategy.testsupport.TestSettings.ENABLED;
import static com.pidluzsnij.strategy.testsupport.TestSettings.NAME;
import static com.pidluzsnij.strategy.testsupport.TestSettings.RATIO;
import static com.pidluzsnij.strategy.testsupport.TestSettings.SCHEMA;
import static com.pidluzsnij.strategy.testsupport.TestSettings.VOLUME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Root application-settings model: defaults, validation and normalization. */
class ApplicationSettingsTest {

    private static Map<String, Object> table(Object... keyValues) {
        Map<String, Object> table = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            table.put((String) keyValues[i], keyValues[i + 1]);
        }
        return table;
    }

    private static PersistedSettings complete() {
        return new PersistedSettings(table(
                "volume", 70L,
                "test", table("name", "custom", "count", 7L, "enabled", false),
                "graphics", table("detail", table("ratio", 0.25))));
    }

    @Test
    void productionModelDefinesOnlyTheVideoSettings() {
        assertEquals(List.of(VideoSettings.FULLSCREEN, VideoSettings.RESOLUTION), ApplicationSettings.SCHEMA.settings());
        ApplicationSettings defaults = ApplicationSettings.defaults(ApplicationSettings.SCHEMA);
        assertEquals(List.of("video"), List.copyOf(defaults.toPersisted().root().keySet()));
    }

    @Test
    void defaultsHoldEveryDefaultValue() {
        ApplicationSettings defaults = ApplicationSettings.defaults(SCHEMA);
        assertEquals(50, defaults.get(VOLUME));
        assertEquals("default-name", defaults.get(NAME));
        assertEquals(3, defaults.get(COUNT));
        assertEquals(true, defaults.get(ENABLED));
        assertEquals(0.5, defaults.get(RATIO));
    }

    @Test
    void withRejectsValuesViolatingValidation() {
        ApplicationSettings defaults = ApplicationSettings.defaults(SCHEMA);
        assertEquals(9, defaults.with(COUNT, 9).get(COUNT));
        assertEquals(3, defaults.get(COUNT), "snapshots are immutable");
        assertThrows(IllegalArgumentException.class, () -> defaults.with(COUNT, 11));
        assertThrows(IllegalArgumentException.class, () -> defaults.with(NAME, " "));
        assertThrows(IllegalArgumentException.class, () -> defaults.with(RATIO, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> defaults.with(NAME, null));
    }

    @Test
    void settingsOutsideTheSchemaAreRejected() {
        Setting<Integer> foreign = Setting.of("foreign", SettingType.INTEGER, 1);
        ApplicationSettings defaults = ApplicationSettings.defaults(SCHEMA);
        assertThrows(IllegalArgumentException.class, () -> defaults.get(foreign));
        assertThrows(IllegalArgumentException.class, () -> defaults.with(foreign, 2));
    }

    @Test
    void schemaRejectsConflictingIdentifiers() {
        Setting<Integer> table = Setting.of("a", SettingType.INTEGER, 1);
        Setting<Integer> nested = Setting.of("a.b", SettingType.INTEGER, 1);
        assertThrows(IllegalArgumentException.class, () -> SettingsSchema.of(table, nested));
        assertThrows(IllegalArgumentException.class, () -> SettingsSchema.of(table, Setting.of("a", SettingType.BOOLEAN, true)));
        assertThrows(IllegalArgumentException.class, () -> Setting.of("a..b", SettingType.INTEGER, 1));
        assertThrows(IllegalArgumentException.class, () -> Setting.of("a", SettingType.INTEGER, 5, v -> v < 5),
                "defaults must be valid");
    }

    @Test
    void completeValidValuesNormalizeWithoutChanges() {
        SettingsNormalization normalization = ApplicationSettings.normalize(SCHEMA, complete());

        assertFalse(normalization.changed());
        ApplicationSettings settings = normalization.settings();
        assertEquals(70, settings.get(VOLUME));
        assertEquals("custom", settings.get(NAME));
        assertEquals(7, settings.get(COUNT));
        assertEquals(false, settings.get(ENABLED));
        assertEquals(0.25, settings.get(RATIO));
        assertEquals(complete(), settings.toPersisted(), "snapshot round-trips");
    }

    @Test
    void missingRecognizedValuesReceiveDefaults() {
        PersistedSettings persisted = new PersistedSettings(table("test", table("name", "custom")));

        SettingsNormalization normalization = ApplicationSettings.normalize(SCHEMA, persisted);

        assertEquals(List.of("volume", "test.count", "test.enabled", "graphics.detail.ratio"), normalization.missing());
        assertTrue(normalization.invalid().isEmpty());
        assertTrue(normalization.unknown().isEmpty());
        assertTrue(normalization.changed());
        assertEquals("custom", normalization.settings().get(NAME));
        assertEquals(3, normalization.settings().get(COUNT));
    }

    @Test
    void incompatibleTypesReceiveDefaults() {
        PersistedSettings persisted = new PersistedSettings(table(
                "volume", 2.5,
                "test", table("name", 12L, "count", "seven", "enabled", "yes"),
                "graphics", table("detail", table("ratio", "half"))));

        SettingsNormalization normalization = ApplicationSettings.normalize(SCHEMA, persisted);

        assertEquals(ApplicationSettings.defaults(SCHEMA), normalization.settings());
        assertEquals(5, normalization.invalid().size());
        assertTrue(normalization.invalid().stream()
                .allMatch(i -> i.reason() == InvalidSetting.Reason.INCOMPATIBLE_TYPE));
        assertEquals(new InvalidSetting("test.count", InvalidSetting.Reason.INCOMPATIBLE_TYPE, "integer"),
                normalization.invalid().get(2));
    }

    @Test
    void outOfRangeIntegerIsAnIncompatibleType() {
        PersistedSettings persisted = new PersistedSettings(table("test", table("count", 1L << 40)));
        SettingsNormalization normalization = ApplicationSettings.normalize(SCHEMA, persisted);
        assertEquals(InvalidSetting.Reason.INCOMPATIBLE_TYPE, normalization.invalid().get(0).reason());
    }

    @Test
    void integralValueConvertsToFloatSetting() {
        PersistedSettings persisted = new PersistedSettings(table("graphics", table("detail", table("ratio", 1L))));
        SettingsNormalization normalization = ApplicationSettings.normalize(SCHEMA, persisted);
        assertEquals(1.0, normalization.settings().get(RATIO));
        assertTrue(normalization.invalid().isEmpty());
    }

    @Test
    void validationFailuresReceiveDefaults() {
        PersistedSettings persisted = new PersistedSettings(table(
                "volume", 101L,
                "test", table("name", "", "count", -1L, "enabled", true),
                "graphics", table("detail", table("ratio", 1.5))));

        SettingsNormalization normalization = ApplicationSettings.normalize(SCHEMA, persisted);

        assertEquals(ApplicationSettings.defaults(SCHEMA), normalization.settings());
        assertEquals(List.of("volume", "test.name", "test.count", "graphics.detail.ratio"),
                normalization.invalid().stream().map(InvalidSetting::id).toList());
        assertTrue(normalization.invalid().stream()
                .allMatch(i -> i.reason() == InvalidSetting.Reason.FAILED_VALIDATION));
    }

    @Test
    void unknownSettingsAreDiscarded() {
        Map<String, Object> root = new LinkedHashMap<>(complete().root());
        root.put("legacy", "x");
        root.put("mods", table("a", 1L, "b", table("c", true)));
        root.put("empty", table());
        Map<String, Object> test = new LinkedHashMap<>(table("name", "custom", "count", 7L, "enabled", false));
        test.put("extra", List.of(1L, 2L));
        root.put("test", test);

        SettingsNormalization normalization = ApplicationSettings.normalize(SCHEMA, new PersistedSettings(root));

        assertEquals(List.of("test.extra", "legacy", "mods.a", "mods.b.c", "empty"), normalization.unknown());
        assertTrue(normalization.missing().isEmpty());
        assertTrue(normalization.invalid().isEmpty());
        assertTrue(normalization.changed());
        assertEquals(complete(), normalization.settings().toPersisted(), "unknown settings are excluded");
    }

    @Test
    void scalarWhereATableIsExpectedIsUnknownAndItsSettingsAreMissing() {
        PersistedSettings persisted = new PersistedSettings(table("volume", 70L, "test", 5L,
                "graphics", table("detail", table("ratio", 0.25))));

        SettingsNormalization normalization = ApplicationSettings.normalize(SCHEMA, persisted);

        assertEquals(List.of("test"), normalization.unknown());
        assertEquals(List.of("test.name", "test.count", "test.enabled"), normalization.missing());
    }

    @Test
    void tableWhereAValueIsExpectedIsIncompatible() {
        PersistedSettings persisted = new PersistedSettings(table("volume", table("level", 3L)));
        SettingsNormalization normalization = ApplicationSettings.normalize(SCHEMA, persisted);
        assertEquals(new InvalidSetting("volume", InvalidSetting.Reason.INCOMPATIBLE_TYPE, "integer"),
                normalization.invalid().get(0));
        assertTrue(normalization.unknown().isEmpty());
    }

    @Test
    void persistedSnapshotIsCompleteIncludingDefaultValues() {
        ApplicationSettings settings = ApplicationSettings.defaults(SCHEMA).with(COUNT, 9);

        Map<String, Object> root = settings.toPersisted().root();

        assertEquals(50L, root.get("volume"));
        assertEquals(table("name", "default-name", "count", 9L, "enabled", true), root.get("test"));
        assertEquals(table("detail", table("ratio", 0.5)), root.get("graphics"));
    }

    @Test
    void diagnosticRepresentationsOmitValues() {
        ApplicationSettings settings = ApplicationSettings.defaults(SCHEMA).with(NAME, "SECRET-MARKER");
        assertFalse(settings.toString().contains("SECRET-MARKER"));
        assertFalse(settings.toPersisted().toString().contains("SECRET-MARKER"));
    }
}
