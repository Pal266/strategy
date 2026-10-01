package com.pidluzsnij.strategy.window;

import com.pidluzsnij.strategy.config.VideoResolution;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Startup resolution from the configured video resolution and the starting monitor. */
class VideoResolutionResolverTest {

    @Test
    void automaticResolutionUsesTheMonitorVideoMode() {
        assertEquals(new ResolvedResolution(new Resolution(1920, 1080), ResolutionSource.MONITOR_VIDEO_MODE),
                ResolutionResolver.resolve(VideoResolution.AUTOMATIC, new VideoMode(1920, 1080, 60)));
    }

    @Test
    void automaticResolutionFallsBackTo1280x720() {
        ResolvedResolution expected = new ResolvedResolution(new Resolution(1280, 720),
                ResolutionSource.AUTOMATIC_FALLBACK);
        assertEquals(expected, ResolutionResolver.resolve(VideoResolution.AUTOMATIC, null));
        assertEquals(expected, ResolutionResolver.resolve(VideoResolution.AUTOMATIC, new VideoMode(0, 0, 0)));
    }

    @Test
    void explicitResolutionIsNotReplacedByTheMonitorResolution() {
        ResolvedResolution expected = new ResolvedResolution(new Resolution(1600, 900),
                ResolutionSource.EXPLICIT_CONFIGURATION);
        assertEquals(expected, ResolutionResolver.resolve(VideoResolution.of(1600, 900), new VideoMode(1920, 1080, 60)));
        assertEquals(expected, ResolutionResolver.resolve(VideoResolution.of(1600, 900), null));
    }

    @Test
    void explicitResolutionNotAdvertisedByTheMonitorIsKept() {
        assertEquals(new Resolution(1234, 567),
                ResolutionResolver.resolve(VideoResolution.of(1234, 567), new VideoMode(2560, 1440, 144)).resolution());
    }
}
