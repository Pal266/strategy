package com.pidluzsnij.strategy.config;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
            Setting.of("video.resolution", new ResolutionType(), VideoResolution.AUTOMATIC, VideoResolution::isValid);

    private VideoSettings() {
    }

    /**
     * Application resolution persisted as a table with exactly a {@code width} and a {@code height}
     * value, each either the string {@code auto} or an integer. Both {@code auto} convert to
     * {@link VideoResolution#AUTOMATIC}; both integers convert to an explicit resolution, whose
     * positivity is a validation rule. Any other shape, including a mixed {@code auto}/numeric
     * combination, cannot be converted, so the resolution is always judged as one logical setting.
     */
    private static final class ResolutionType extends SettingType<VideoResolution> {

        private static final String WIDTH = "width";
        private static final String HEIGHT = "height";
        private static final String AUTO = "auto";

        private ResolutionType() {
            super("resolution (width and height both auto or both integers)", VideoResolution.class);
        }

        @Override
        Optional<VideoResolution> convert(Object persisted) {
            if (!(persisted instanceof Map<?, ?> table)) {
                return Optional.empty();
            }
            Object width = table.get(WIDTH);
            Object height = table.get(HEIGHT);
            if (AUTO.equals(width) && AUTO.equals(height)) {
                return Optional.of(VideoResolution.AUTOMATIC);
            }
            Optional<Integer> numericWidth = SettingType.INTEGER.convert(width);
            Optional<Integer> numericHeight = SettingType.INTEGER.convert(height);
            if (numericWidth.isPresent() && numericHeight.isPresent()) {
                return Optional.of(VideoResolution.of(numericWidth.get(), numericHeight.get()));
            }
            return Optional.empty();
        }

        @Override
        Object persist(VideoResolution value) {
            Map<String, Object> table = new LinkedHashMap<>();
            if (value instanceof VideoResolution.Explicit explicit) {
                table.put(WIDTH, SettingType.INTEGER.persist(explicit.width()));
                table.put(HEIGHT, SettingType.INTEGER.persist(explicit.height()));
            } else {
                table.put(WIDTH, AUTO);
                table.put(HEIGHT, AUTO);
            }
            return table;
        }

        @Override
        Set<String> tableKeys() {
            return Set.of(WIDTH, HEIGHT);
        }
    }
}
