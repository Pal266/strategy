package com.pidluzsnij.strategy.config;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Definition of one recognized application setting: its identifier, type, default value
 * and validation rule.
 * <p>
 * The identifier is a dot-separated path of segments (for example {@code video.width});
 * each segment consists of ASCII letters, digits, {@code _} or {@code -}.
 *
 * @param <T> the Java type of the setting's values
 */
public final class Setting<T> {

    private static final Pattern SEGMENT = Pattern.compile("[A-Za-z0-9_-]+");

    private final String id;
    private final List<String> path;
    private final SettingType<T> type;
    private final T defaultValue;
    private final Predicate<? super T> validation;
    private final boolean nonTableSectionInvalid;

    private Setting(String id, SettingType<T> type, T defaultValue, Predicate<? super T> validation) {
        this(id, type, defaultValue, validation, false);
    }

    private Setting(String id, SettingType<T> type, T defaultValue, Predicate<? super T> validation,
                    boolean nonTableSectionInvalid) {
        this.nonTableSectionInvalid = nonTableSectionInvalid;
        this.id = Objects.requireNonNull(id, "id");
        this.type = Objects.requireNonNull(type, "type");
        this.validation = Objects.requireNonNull(validation, "validation");
        this.path = List.of(id.split("\\.", -1));
        for (String segment : path) {
            if (!SEGMENT.matcher(segment).matches()) {
                throw new IllegalArgumentException("invalid setting identifier: " + id);
            }
        }
        this.defaultValue = type.cast(defaultValue);
        if (!isValid(this.defaultValue)) {
            throw new IllegalArgumentException("default value of setting " + id + " is not valid");
        }
    }

    /** A setting whose every value of the given type is valid. */
    public static <T> Setting<T> of(String id, SettingType<T> type, T defaultValue) {
        return new Setting<>(id, type, defaultValue, value -> true);
    }

    /** A setting whose values must additionally satisfy {@code validation}. */
    public static <T> Setting<T> of(String id, SettingType<T> type, T defaultValue,
                                    Predicate<? super T> validation) {
        return new Setting<>(id, type, defaultValue, validation);
    }

    /**
     * @return a copy of this setting that is reported as invalid, rather than missing, when one of its
     * enclosing sections is persisted as something other than a table
     */
    public Setting<T> invalidWhenSectionIsNotATable() {
        return new Setting<>(id, type, defaultValue, validation, true);
    }

    /** @return whether a non-table enclosing section makes this setting invalid rather than missing */
    public boolean isInvalidWhenSectionIsNotATable() {
        return nonTableSectionInvalid;
    }

    /** @return the setting's identifier */
    public String id() {
        return id;
    }

    /** @return the identifier's segments */
    public List<String> path() {
        return path;
    }

    public SettingType<T> type() {
        return type;
    }

    public T defaultValue() {
        return defaultValue;
    }

    /** @return whether {@code value} is a valid effective value for this setting */
    public boolean isValid(T value) {
        return value != null && type.accepts(value) && validation.test(value);
    }

    @Override
    public String toString() {
        return id + " (" + type.name() + ")";
    }
}
