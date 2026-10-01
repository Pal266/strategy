package com.pidluzsnij.strategy.config;

/** Settings of the {@code video} section of the application settings. */
public final class VideoSettings {

    /** Whether the application window starts in fullscreen ({@code true}) or windowed mode. */
    public static final Setting<Boolean> FULLSCREEN =
            Setting.of("video.fullscreen", SettingType.BOOLEAN, true);

    /**
     * Application resolution, persisted as {@code video.resolution.width} and
     * {@code video.resolution.height}; valid when both are {@code auto} or both are positive
     * integers. Whether a monitor supports explicit dimensions is not a configuration concern.
     */
    public static final Setting<VideoResolution> RESOLUTION =
            Setting.of("video.resolution", SettingType.RESOLUTION, VideoResolution.AUTOMATIC, VideoResolution::isValid);

    private VideoSettings() {
    }
}
