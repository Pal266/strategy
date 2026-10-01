package com.pidluzsnij.strategy.window;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResolutionResolverTest {

    @Test
    void detectedMonitorResolutionIsUsed() {
        assertEquals(new Resolution(1920, 1080), ResolutionResolver.resolve(new VideoMode(1920, 1080, 60)));
    }

    @Test
    void unavailableVideoModeFallsBackTo1280x720() {
        assertEquals(new Resolution(1280, 720), ResolutionResolver.resolve(null));
    }

    @Test
    void undeterminedVideoModeDimensionsFallBackTo1280x720() {
        assertEquals(new Resolution(1280, 720), ResolutionResolver.resolve(new VideoMode(0, 0, 0)));
    }
}
