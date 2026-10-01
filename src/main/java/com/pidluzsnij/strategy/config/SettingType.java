package com.pidluzsnij.strategy.config;

import java.util.Objects;
import java.util.Optional;

/**
 * Value type of a setting: converts a persisted, format-independent value to the
 * setting's Java type and back.
 *
 * @param <T> the Java type of the setting's values
 */
public abstract class SettingType<T> {

    /** {@code true}/{@code false}; only boolean persisted values convert. */
    public static final SettingType<Boolean> BOOLEAN = new SettingType<>("boolean", Boolean.class) {
        @Override
        Optional<Boolean> convert(Object persisted) {
            return persisted instanceof Boolean value ? Optional.of(value) : Optional.empty();
        }

        @Override
        Object persist(Boolean value) {
            return value;
        }
    };

    /** 32-bit signed integer; only integral persisted values within range convert. */
    public static final SettingType<Integer> INTEGER = new SettingType<>("integer", Integer.class) {
        @Override
        Optional<Integer> convert(Object persisted) {
            if (persisted instanceof Integer || persisted instanceof Long
                    || persisted instanceof Short || persisted instanceof Byte) {
                long value = ((Number) persisted).longValue();
                if (value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) {
                    return Optional.of((int) value);
                }
            }
            return Optional.empty();
        }

        @Override
        Object persist(Integer value) {
            return value.longValue();
        }
    };

    /** Finite double-precision number; floating-point and integral persisted values convert. */
    public static final SettingType<Double> DOUBLE = new SettingType<>("float", Double.class) {
        @Override
        Optional<Double> convert(Object persisted) {
            if (persisted instanceof Double || persisted instanceof Float || persisted instanceof Integer
                    || persisted instanceof Long || persisted instanceof Short || persisted instanceof Byte) {
                double value = ((Number) persisted).doubleValue();
                if (Double.isFinite(value)) {
                    return Optional.of(value);
                }
            }
            return Optional.empty();
        }

        @Override
        boolean accepts(Double value) {
            return Double.isFinite(value);
        }

        @Override
        Object persist(Double value) {
            return value;
        }
    };

    /** Text; only string persisted values convert. */
    public static final SettingType<String> STRING = new SettingType<>("string", String.class) {
        @Override
        Optional<String> convert(Object persisted) {
            return persisted instanceof String value ? Optional.of(value) : Optional.empty();
        }

        @Override
        Object persist(String value) {
            return value;
        }
    };

    private final String name;
    private final Class<T> javaType;

    private SettingType(String name, Class<T> javaType) {
        this.name = name;
        this.javaType = javaType;
    }

    /** @return the type's name, used in diagnostics */
    public String name() {
        return name;
    }

    Class<T> javaType() {
        return javaType;
    }

    /** @return the converted value, or empty when the persisted value cannot be converted */
    abstract Optional<T> convert(Object persisted);

    /** @return the format-independent persisted representation of a value */
    abstract Object persist(T value);

    /** @return whether a non-null value is representable by this type */
    boolean accepts(T value) {
        return true;
    }

    T cast(Object value) {
        Objects.requireNonNull(value, "value");
        return javaType.cast(value);
    }

    @Override
    public String toString() {
        return name;
    }
}
