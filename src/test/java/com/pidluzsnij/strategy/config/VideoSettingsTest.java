package com.pidluzsnij.strategy.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static com.pidluzsnij.strategy.config.VideoSettings.FULLSCREEN;
import static com.pidluzsnij.strategy.config.VideoSettings.RESOLUTION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Video settings in the application-settings model: defaults, validation and normalization. */
class VideoSettingsTest {

    private static final SettingsSchema SCHEMA = ApplicationSettings.SCHEMA;

    private static Map<String, Object> table(Object... keyValues) {
        Map<String, Object> table = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            table.put((String) keyValues[i], keyValues[i + 1]);
        }
        return table;
    }

    private static PersistedSettings video(Object fullscreen, Object resolution) {
        return new PersistedSettings(table("video", table("fullscreen", fullscreen, "resolution", resolution)));
    }

    private static PersistedSettings withResolution(Object width, Object height) {
        return video(true, table("width", width, "height", height));
    }

    private static SettingsNormalization normalize(PersistedSettings persisted) {
        return ApplicationSettings.normalize(SCHEMA, persisted);
    }

    // --- Defaults -------------------------------------------------------------------------

    @Test
    void fullscreenDefaultsToTrue() {
        assertEquals(true, ApplicationSettings.defaults(SCHEMA).get(FULLSCREEN));
        assertEquals("video.fullscreen", FULLSCREEN.id());
    }

    @Test
    void resolutionDefaultsToAutoForWidthAndHeight() {
        ApplicationSettings defaults = ApplicationSettings.defaults(SCHEMA);

        assertSame(VideoResolution.AUTOMATIC, defaults.get(RESOLUTION));
        assertEquals("video.resolution", RESOLUTION.id());
        assertEquals(table("video", table("fullscreen", true, "resolution", table("width", "auto", "height", "auto"))),
                defaults.toPersisted().root());
    }

    @Test
    void missingVideoSettingsReceiveTheirDefaults() {
        SettingsNormalization normalization = normalize(new PersistedSettings(table()));

        assertEquals(List.of("video.fullscreen", "video.resolution"), normalization.missing());
        assertTrue(normalization.invalid().isEmpty());
        assertEquals(ApplicationSettings.defaults(SCHEMA), normalization.settings());
    }

    // --- Fullscreen -----------------------------------------------------------------------

    @Test
    void configuredFullscreenValuesAreKept() {
        for (boolean fullscreen : List.of(true, false)) {
            SettingsNormalization normalization = normalize(video(fullscreen, table("width", "auto", "height", "auto")));
            assertFalse(normalization.changed());
            assertEquals(fullscreen, normalization.settings().get(FULLSCREEN));
        }
    }

    @Test
    void nonBooleanFullscreenIsInvalidAndDefaultsToTrue() {
        for (Object value : List.of("false", 0L, 1L, 1.0, table("on", true))) {
            SettingsNormalization normalization = normalize(video(value, table("width", "auto", "height", "auto")));

            assertEquals(true, normalization.settings().get(FULLSCREEN), String.valueOf(value));
            assertEquals(List.of(new InvalidSetting("video.fullscreen", InvalidSetting.Reason.INCOMPATIBLE_TYPE,
                    "boolean")), normalization.invalid());
            assertTrue(normalization.unknown().isEmpty());
        }
    }

    // --- Resolution -----------------------------------------------------------------------

    @Test
    void autoForBothDimensionsIsAutomaticResolution() {
        SettingsNormalization normalization = normalize(withResolution("auto", "auto"));

        assertFalse(normalization.changed());
        assertSame(VideoResolution.AUTOMATIC, normalization.settings().get(RESOLUTION));
        assertFalse(normalization.settings().get(RESOLUTION) instanceof VideoResolution.Explicit,
                "auto is never exposed as numeric dimensions");
    }

    @Test
    void positiveIntegersForBothDimensionsAreAnExplicitResolution() {
        SettingsNormalization normalization = normalize(withResolution(1600L, 900L));

        assertFalse(normalization.changed());
        assertEquals(VideoResolution.of(1600, 900), normalization.settings().get(RESOLUTION));
    }

    @Test
    void explicitDimensionsAreValidWithoutAnyMonitorMode() {
        // No monitor information is part of the model: unusual positive dimensions remain valid.
        for (VideoResolution resolution : List.of(VideoResolution.of(1, 1), VideoResolution.of(1234, 567),
                VideoResolution.of(Integer.MAX_VALUE, Integer.MAX_VALUE))) {
            assertTrue(RESOLUTION.isValid(resolution), resolution.toString());
            SettingsNormalization normalization = normalize(new PersistedSettings(
                    ApplicationSettings.defaults(SCHEMA).with(RESOLUTION, resolution).toPersisted().root()));
            assertFalse(normalization.changed(), resolution.toString());
            assertEquals(resolution, normalization.settings().get(RESOLUTION));
        }
    }

    static Stream<PersistedSettings> incompatibleResolutions() {
        return Stream.of(
                withResolution(1600L, "auto"),
                withResolution("auto", 900L),
                withResolution("auto", "big"),
                withResolution("AUTO", "AUTO"),
                withResolution(1600.0, 900L),
                withResolution(true, false),
                withResolution(1L << 40, 900L),
                withResolution(List.of(1600L), 900L),
                video(true, table("width", 1600L)),
                video(true, table("height", "auto")),
                video(true, table()),
                video(true, "auto"),
                video(true, 1600L));
    }

    @ParameterizedTest
    @MethodSource("incompatibleResolutions")
    void resolutionNotFormingAValidRepresentationIsInvalidAsAWhole(PersistedSettings persisted) {
        SettingsNormalization normalization = normalize(persisted);

        assertSame(VideoResolution.AUTOMATIC, normalization.settings().get(RESOLUTION));
        assertEquals(1, normalization.invalid().size());
        InvalidSetting invalid = normalization.invalid().get(0);
        assertEquals("video.resolution", invalid.id());
        assertEquals(InvalidSetting.Reason.INCOMPATIBLE_TYPE, invalid.reason());
        assertEquals(RESOLUTION.type().name(), invalid.expectedType());
        assertTrue(normalization.missing().isEmpty());
        assertTrue(normalization.changed());
        assertEquals(table("width", "auto", "height", "auto"),
                asTable(asTable(normalization.settings().toPersisted().root().get("video")).get("resolution")),
                "neither configured dimension is retained");
    }

    @Test
    void nonPositiveDimensionsFailValidationAsAWhole() {
        for (long[] dimensions : new long[][] {{0, 900}, {1600, 0}, {-1600, 900}, {1600, -1}, {0, 0}}) {
            SettingsNormalization normalization = normalize(withResolution(dimensions[0], dimensions[1]));

            assertSame(VideoResolution.AUTOMATIC, normalization.settings().get(RESOLUTION));
            assertEquals(List.of(new InvalidSetting("video.resolution", InvalidSetting.Reason.FAILED_VALIDATION,
                    RESOLUTION.type().name())), normalization.invalid());
        }
    }

    @Test
    void oneValidAndOneInvalidDimensionAreNotRetainedIndependently() {
        for (PersistedSettings persisted : List.of(withResolution(1600L, -900L), withResolution(1600L, "x"),
                withResolution("auto", 0L), withResolution("auto", "x"))) {
            SettingsNormalization normalization = normalize(persisted);
            assertSame(VideoResolution.AUTOMATIC, normalization.settings().get(RESOLUTION));
            assertEquals(1, normalization.invalid().size());
        }
    }

    @Test
    void invalidResolutionsAreRejectedBySnapshots() {
        ApplicationSettings defaults = ApplicationSettings.defaults(SCHEMA);
        assertThrows(IllegalArgumentException.class, () -> defaults.with(RESOLUTION, VideoResolution.of(0, 900)));
        assertThrows(IllegalArgumentException.class, () -> defaults.with(RESOLUTION, VideoResolution.of(1600, -1)));
        assertThrows(IllegalArgumentException.class, () -> defaults.with(RESOLUTION, null));
        assertEquals(VideoResolution.of(1600, 900),
                defaults.with(RESOLUTION, VideoResolution.of(1600, 900)).get(RESOLUTION));
    }

    @Test
    void explicitResolutionIsPersistedAsNumericWidthAndHeight() {
        ApplicationSettings settings = ApplicationSettings.defaults(SCHEMA)
                .with(FULLSCREEN, false).with(RESOLUTION, VideoResolution.of(1600, 900));

        assertEquals(table("video", table("fullscreen", false, "resolution", table("width", 1600L, "height", 900L))),
                settings.toPersisted().root());
    }

    @Test
    void otherKeysInTheResolutionTableAreUnknownSettings() {
        PersistedSettings persisted = video(true, table("width", 1600L, "height", 900L, "depth", 32L,
                "extra", table("a", true)));

        SettingsNormalization normalization = normalize(persisted);

        assertEquals(VideoResolution.of(1600, 900), normalization.settings().get(RESOLUTION));
        assertEquals(List.of("video.resolution.depth", "video.resolution.extra.a"), normalization.unknown());
        assertTrue(normalization.invalid().isEmpty());
        assertTrue(normalization.changed());
    }

    @Test
    void diagnosticRepresentationsOmitVideoValues() {
        ApplicationSettings settings = ApplicationSettings.defaults(SCHEMA)
                .with(RESOLUTION, VideoResolution.of(4321, 1234));
        assertFalse(settings.toString().contains("4321"));
        assertFalse(settings.toPersisted().toString().contains("4321"));
        assertInstanceOf(VideoResolution.Explicit.class, settings.get(RESOLUTION));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asTable(Object value) {
        return (Map<String, Object>) value;
    }
}
