package com.pidluzsnij.strategy.config.persistence.toml;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.pidluzsnij.strategy.config.ApplicationSettings;
import com.pidluzsnij.strategy.config.VideoResolution;
import com.pidluzsnij.strategy.config.VideoSettings;
import com.pidluzsnij.strategy.config.persistence.LoadResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** TOML representation of the production video settings. */
class VideoSettingsTomlTest {

    @TempDir
    Path temp;

    private TomlConfigurationPersistence persistence() {
        return new TomlConfigurationPersistence(ApplicationSettings.SCHEMA, temp);
    }

    private String savedText(ApplicationSettings settings) throws Exception {
        persistence().save(settings);
        return Files.readString(persistence().file(), StandardCharsets.UTF_8);
    }

    @Test
    void explicitResolutionAndWindowedModeArePersistedNumerically() throws Exception {
        ApplicationSettings settings = ApplicationSettings.defaults(ApplicationSettings.SCHEMA)
                .with(VideoSettings.FULLSCREEN, false)
                .with(VideoSettings.RESOLUTION, VideoResolution.of(1600, 900));

        String text = savedText(settings);

        CommentedConfig parsed = TomlConfigurationPersistenceTest.parseToml10(text);
        assertEquals(List.of("video"), List.copyOf(parsed.valueMap().keySet()));
        assertEquals(false, parsed.get("video.fullscreen"));
        assertInstanceOf(Number.class, parsed.get("video.resolution.width"));
        assertInstanceOf(Number.class, parsed.get("video.resolution.height"));
        assertEquals(1600, parsed.<Number>get("video.resolution.width").intValue());
        assertEquals(900, parsed.<Number>get("video.resolution.height").intValue());
        assertTrue(text.contains("[video]"), text);
        assertTrue(text.contains("fullscreen = false"), text);
        assertTrue(text.contains("width = 1600"), text);
        assertTrue(text.contains("height = 900"), text);
    }

    @Test
    void automaticResolutionIsPersistedAsAutoForBothDimensions() throws Exception {
        String text = savedText(ApplicationSettings.defaults(ApplicationSettings.SCHEMA));

        CommentedConfig parsed = TomlConfigurationPersistenceTest.parseToml10(text);
        assertEquals(true, parsed.get("video.fullscreen"));
        assertEquals("auto", parsed.get("video.resolution.width"));
        assertEquals("auto", parsed.get("video.resolution.height"));
    }

    @Test
    void automaticResolutionRoundTripsWithoutMonitorDimensions() throws Exception {
        String text = savedText(ApplicationSettings.defaults(ApplicationSettings.SCHEMA));

        LoadResult.Loaded loaded = assertInstanceOf(LoadResult.Loaded.class, persistence().load());

        assertFalse(loaded.normalization().changed());
        assertSame(VideoResolution.AUTOMATIC, loaded.settings().get(VideoSettings.RESOLUTION));
        assertFalse(text.matches("(?s).*(width|height) = \\d.*"), "no numeric dimensions are persisted: " + text);
    }

    @Test
    void handWrittenVideoSettingsLoad() throws Exception {
        Files.createDirectories(persistence().file().getParent());
        Files.writeString(persistence().file(), """
                [video]
                fullscreen = false

                [video.resolution]
                width = 2560
                height = 1440
                """);

        LoadResult.Loaded loaded = assertInstanceOf(LoadResult.Loaded.class, persistence().load());

        assertFalse(loaded.normalization().changed());
        assertEquals(false, loaded.settings().get(VideoSettings.FULLSCREEN));
        assertEquals(VideoResolution.of(2560, 1440), loaded.settings().get(VideoSettings.RESOLUTION));
    }
}
