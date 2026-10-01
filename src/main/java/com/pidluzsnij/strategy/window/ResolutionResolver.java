package com.pidluzsnij.strategy.window;

import com.pidluzsnij.strategy.config.VideoResolution;

import java.util.Objects;

/** Chooses the application resolution from the configured resolution and the starting monitor's video mode. */
public final class ResolutionResolver {

    private ResolutionResolver() {
    }

    /**
     * @param configured the effective configured resolution
     * @param videoMode  the starting monitor's current video mode, or {@code null} when unavailable
     * @return the configured dimensions when explicit; otherwise the video mode's resolution, or
     * {@link Resolution#FALLBACK} when it cannot be determined
     */
    public static ResolvedResolution resolve(VideoResolution configured, VideoMode videoMode) {
        Objects.requireNonNull(configured, "configured");
        if (configured instanceof VideoResolution.Explicit explicit) {
            return new ResolvedResolution(new Resolution(explicit.width(), explicit.height()),
                    ResolutionSource.EXPLICIT_CONFIGURATION);
        }
        if (!isDetermined(videoMode)) {
            return new ResolvedResolution(Resolution.FALLBACK, ResolutionSource.AUTOMATIC_FALLBACK);
        }
        return new ResolvedResolution(new Resolution(videoMode.width(), videoMode.height()),
                ResolutionSource.MONITOR_VIDEO_MODE);
    }

    /**
     * @param videoMode the starting monitor's current video mode, or {@code null} when unavailable
     * @return the video mode's resolution, or {@link Resolution#FALLBACK} when it cannot be determined
     */
    public static Resolution resolve(VideoMode videoMode) {
        return resolve(VideoResolution.AUTOMATIC, videoMode).resolution();
    }

    static boolean isDetermined(VideoMode videoMode) {
        return videoMode != null && videoMode.width() > 0 && videoMode.height() > 0;
    }
}
