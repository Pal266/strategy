package com.pidluzsnij.strategy.config;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

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

    /**
     * Application resolution persisted as a table with exactly a {@code width} and a {@code height}
     * value, each either the string {@code auto} or an integer. Both {@code auto} convert to
     * {@link VideoResolution#AUTOMATIC}; both integers convert to an explicit resolution, whose
     * positivity is a validation rule. Any other shape, including a mixed {@code auto}/numeric
     * combination, cannot be converted, so the resolution is always judged as one logical setting.
     */
    public static final SettingType<VideoResolution> RESOLUTION =
            new SettingType<>("resolution (width and height both auto or both integers)", VideoResolution.class) {

                private static final String WIDTH = "width";
                private static final String HEIGHT = "height";
                private static final String AUTO = "auto";

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
                    Optional<Integer> numericWidth = INTEGER.convert(width);
                    Optional<Integer> numericHeight = INTEGER.convert(height);
                    if (numericWidth.isPresent() && numericHeight.isPresent()) {
                        return Optional.of(VideoResolution.of(numericWidth.get(), numericHeight.get()));
                    }
                    return Optional.empty();
                }

                @Override
                Object persist(VideoResolution value) {
                    Map<String, Object> table = new LinkedHashMap<>();
                    if (value instanceof VideoResolution.Explicit explicit) {
                        table.put(WIDTH, INTEGER.persist(explicit.width()));
                        table.put(HEIGHT, INTEGER.persist(explicit.height()));
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

    /**
     * @return the keys of the table that persists one value of this type, or an empty set when
     * values are not persisted as tables; other keys in such a table are unknown settings
     */
    Set<String> tableKeys() {
        return Set.of();
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
