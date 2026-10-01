package com.pidluzsnij.strategy.window;

/** Chooses the application resolution from the starting monitor's current video mode. */
public final class ResolutionResolver {

    private ResolutionResolver() {
    }

    /**
     * @param videoMode the starting monitor's current video mode, or {@code null} when unavailable
     * @return the video mode's resolution, or {@link Resolution#FALLBACK} when it cannot be determined
     */
    public static Resolution resolve(VideoMode videoMode) {
        if (!isDetermined(videoMode)) {
            return Resolution.FALLBACK;
        }
        return new Resolution(videoMode.width(), videoMode.height());
    }

    static boolean isDetermined(VideoMode videoMode) {
        return videoMode != null && videoMode.width() > 0 && videoMode.height() > 0;
    }
}
