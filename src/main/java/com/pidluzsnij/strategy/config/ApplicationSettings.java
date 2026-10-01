package com.pidluzsnij.strategy.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Root application-settings model: an immutable, complete snapshot holding a valid
 * effective value for every setting of its {@link SettingsSchema}.
 * <p>
 * Defaults and validation rules belong to the settings' definitions; this class does not
 * know how or where settings are persisted.
 */
public final class ApplicationSettings {

    /** Settings recognized by the application. */
    public static final SettingsSchema SCHEMA = SettingsSchema.of(VideoSettings.FULLSCREEN, VideoSettings.RESOLUTION);

    private final SettingsSchema schema;
    private final Map<Setting<?>, Object> values;

    private ApplicationSettings(SettingsSchema schema, Map<Setting<?>, Object> values) {
        this.schema = schema;
        this.values = values;
    }

    /** @return settings holding the default value of every setting in {@code schema} */
    public static ApplicationSettings defaults(SettingsSchema schema) {
        Map<Setting<?>, Object> values = new LinkedHashMap<>();
        for (Setting<?> setting : schema.settings()) {
            values.put(setting, setting.defaultValue());
        }
        return new ApplicationSettings(schema, values);
    }

    /**
     * Produces complete effective settings from persisted values: recognized settings that
     * are missing or invalid receive their default, unknown persisted settings are dropped.
     */
    public static SettingsNormalization normalize(SettingsSchema schema, PersistedSettings persisted) {
        Map<Setting<?>, Object> values = new LinkedHashMap<>();
        List<String> missing = new ArrayList<>();
        List<InvalidSetting> invalid = new ArrayList<>();
        for (Setting<?> setting : schema.settings()) {
            values.put(setting, effectiveValue(setting, persisted.valueAt(setting.path()), missing, invalid));
        }
        List<String> unknown = new ArrayList<>();
        collectUnknown(schema, List.of(), persisted.root(), unknown);
        return new SettingsNormalization(new ApplicationSettings(schema, values), missing, invalid, unknown);
    }

    private static <T> T effectiveValue(Setting<T> setting, Object persisted, List<String> missing,
                                        List<InvalidSetting> invalid) {
        if (persisted == null) {
            missing.add(setting.id());
            return setting.defaultValue();
        }
        Optional<T> converted = setting.type().convert(persisted);
        if (converted.isEmpty()) {
            invalid.add(new InvalidSetting(setting.id(), InvalidSetting.Reason.INCOMPATIBLE_TYPE,
                    setting.type().name()));
            return setting.defaultValue();
        }
        if (!setting.isValid(converted.get())) {
            invalid.add(new InvalidSetting(setting.id(), InvalidSetting.Reason.FAILED_VALIDATION,
                    setting.type().name()));
            return setting.defaultValue();
        }
        return converted.get();
    }

    private static void collectUnknown(SettingsSchema schema, List<String> tablePath, Map<String, Object> table,
                                       List<String> unknown) {
        for (Map.Entry<String, Object> entry : table.entrySet()) {
            List<String> path = append(tablePath, entry.getKey());
            Optional<Setting<?>> setting = schema.settingAt(path);
            if (setting.isPresent()) {
                collectUnknownTableKeys(setting.get(), path, entry.getValue(), unknown);
                continue;
            }
            if (entry.getValue() instanceof Map<?, ?> && schema.isTablePath(path)) {
                collectUnknown(schema, path, asTable(entry.getValue()), unknown);
            } else {
                collectLeaves(path, entry.getValue(), unknown);
            }
        }
    }

    /** Reports keys of a table-persisted setting's table that are not part of its representation. */
    private static void collectUnknownTableKeys(Setting<?> setting, List<String> path, Object value,
                                                List<String> unknown) {
        Set<String> keys = setting.type().tableKeys();
        if (keys.isEmpty() || !(value instanceof Map<?, ?>)) {
            return;
        }
        asTable(value).forEach((key, child) -> {
            if (!keys.contains(key)) {
                collectLeaves(append(path, key), child, unknown);
            }
        });
    }

    /** Reports every leaf of an unknown subtree, or the subtree itself when it has none. */
    private static void collectLeaves(List<String> path, Object value, List<String> unknown) {
        if (value instanceof Map<?, ?> nested && !nested.isEmpty()) {
            asTable(value).forEach((key, child) -> collectLeaves(append(path, key), child, unknown));
        } else {
            unknown.add(String.join(".", path));
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asTable(Object value) {
        return (Map<String, Object>) value;
    }

    private static List<String> append(List<String> path, String key) {
        List<String> result = new ArrayList<>(path.size() + 1);
        result.addAll(path);
        result.add(key);
        return result;
    }

    /** @return the schema defining this snapshot's settings */
    public SettingsSchema schema() {
        return schema;
    }

    /** @return the effective value of {@code setting} */
    public <T> T get(Setting<T> setting) {
        requireRecognized(setting);
        return setting.type().cast(values.get(setting));
    }

    /**
     * @return a copy of these settings with {@code setting} set to {@code value}
     * @throws IllegalArgumentException when the value is not valid for the setting
     */
    public <T> ApplicationSettings with(Setting<T> setting, T value) {
        requireRecognized(setting);
        if (!setting.isValid(value)) {
            throw new IllegalArgumentException("invalid value for setting " + setting.id());
        }
        Map<Setting<?>, Object> copy = new LinkedHashMap<>(values);
        copy.put(setting, value);
        return new ApplicationSettings(schema, copy);
    }

    /** @return the complete snapshot, including default-valued settings, as format-independent values */
    public PersistedSettings toPersisted() {
        Map<String, Object> root = new LinkedHashMap<>();
        for (Setting<?> setting : schema.settings()) {
            Map<String, Object> table = root;
            List<String> path = setting.path();
            for (String key : path.subList(0, path.size() - 1)) {
                table = asTable(table.computeIfAbsent(key, k -> new LinkedHashMap<String, Object>()));
            }
            table.put(path.get(path.size() - 1), persistedValue(setting));
        }
        return new PersistedSettings(root);
    }

    private <T> Object persistedValue(Setting<T> setting) {
        return setting.type().persist(get(setting));
    }

    private void requireRecognized(Setting<?> setting) {
        Objects.requireNonNull(setting, "setting");
        if (!schema.contains(setting)) {
            throw new IllegalArgumentException("setting is not part of this schema: " + setting.id());
        }
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ApplicationSettings that && schema == that.schema && values.equals(that.values);
    }

    @Override
    public int hashCode() {
        return values.hashCode();
    }

    /** Deliberately omits values so that configuration contents never reach diagnostics. */
    @Override
    public String toString() {
        return "ApplicationSettings[" + values.size() + " settings]";
    }
}
