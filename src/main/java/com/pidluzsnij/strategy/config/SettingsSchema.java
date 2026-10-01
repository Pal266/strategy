package com.pidluzsnij.strategy.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Ordered set of recognized settings that make up an application-settings model. */
public final class SettingsSchema {

    private final List<Setting<?>> settings;
    private final Map<List<String>, Setting<?>> byPath;

    private SettingsSchema(List<Setting<?>> settings) {
        this.settings = List.copyOf(settings);
        this.byPath = new HashMap<>();
        for (Setting<?> setting : this.settings) {
            for (Setting<?> other : byPath.values()) {
                if (isPrefix(setting.path(), other.path()) || isPrefix(other.path(), setting.path())) {
                    throw new IllegalArgumentException("conflicting setting identifiers: "
                            + other.id() + " and " + setting.id());
                }
            }
            byPath.put(setting.path(), setting);
        }
    }

    public static SettingsSchema of(Setting<?>... settings) {
        return new SettingsSchema(List.of(settings));
    }

    public static SettingsSchema of(List<Setting<?>> settings) {
        return new SettingsSchema(new ArrayList<>(settings));
    }

    /** @return the recognized settings in definition order */
    public List<Setting<?>> settings() {
        return settings;
    }

    public boolean contains(Setting<?> setting) {
        return byPath.get(setting.path()) == setting;
    }

    Optional<Setting<?>> settingAt(List<String> path) {
        return Optional.ofNullable(byPath.get(path));
    }

    /** @return whether {@code path} is a proper prefix of a recognized setting's path */
    boolean isTablePath(List<String> path) {
        for (Setting<?> setting : settings) {
            if (setting.path().size() > path.size() && isPrefix(path, setting.path())) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return whether {@code path} is a proper prefix of a recognized setting that is invalid, rather than
     * missing, when its section is not a table
     */
    boolean isTablePathOfSettingInvalidWhenNotATable(List<String> path) {
        for (Setting<?> setting : settings) {
            if (setting.isInvalidWhenSectionIsNotATable() && setting.path().size() > path.size()
                    && isPrefix(path, setting.path())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isPrefix(List<String> prefix, List<String> path) {
        return prefix.size() <= path.size() && path.subList(0, prefix.size()).equals(prefix);
    }
}
