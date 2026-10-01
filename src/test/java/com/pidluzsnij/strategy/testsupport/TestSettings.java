package com.pidluzsnij.strategy.testsupport;

import com.pidluzsnij.strategy.config.Setting;
import com.pidluzsnij.strategy.config.SettingType;
import com.pidluzsnij.strategy.config.SettingsSchema;

/** Test-only settings exercising every supported type, nesting level and validation. */
public final class TestSettings {

    public static final Setting<String> NAME =
            Setting.of("test.name", SettingType.STRING, "default-name", v -> !v.isBlank() && v.length() <= 32);
    public static final Setting<Integer> COUNT =
            Setting.of("test.count", SettingType.INTEGER, 3, v -> v >= 0 && v <= 10);
    public static final Setting<Boolean> ENABLED =
            Setting.of("test.enabled", SettingType.BOOLEAN, true);
    public static final Setting<Double> RATIO =
            Setting.of("graphics.detail.ratio", SettingType.DOUBLE, 0.5, v -> v >= 0 && v <= 1);
    public static final Setting<Integer> VOLUME =
            Setting.of("volume", SettingType.INTEGER, 50, v -> v >= 0 && v <= 100);

    /** The current test model. */
    public static final SettingsSchema SCHEMA = SettingsSchema.of(VOLUME, NAME, COUNT, ENABLED, RATIO);

    /** An older test model that does not yet know {@link #RATIO}. */
    public static final SettingsSchema OLDER_SCHEMA = SettingsSchema.of(VOLUME, NAME, COUNT, ENABLED);

    /** Valid TOML containing exactly the recognized settings of {@link #SCHEMA}, all valid. */
    public static final String COMPLETE_VALID_TOML = """
            volume = 70

            [test]
            name = "custom"
            count = 7
            enabled = false

            [graphics.detail]
            ratio = 0.25
            """;

    private TestSettings() {
    }
}
