package com.pidluzsnij.strategy.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Format-independent tree of persisted configuration values.
 * <p>
 * Tables are {@link Map}s from key to value; leaves are plain Java values such as
 * {@link Boolean}, {@link Long}, {@link Double}, {@link String}, {@link List} or
 * {@code java.time} values. Persistence implementations translate their storage format
 * to and from this tree; the application-settings model interprets it.
 */
public final class PersistedSettings {

    private final Map<String, Object> root;

    public PersistedSettings(Map<String, ?> root) {
        this.root = copyTable(Objects.requireNonNull(root, "root"));
    }

    /** @return the root table; nested tables are unmodifiable {@link Map}s in insertion order */
    public Map<String, Object> root() {
        return root;
    }

    /** @return the value at {@code path}, or {@code null} when absent */
    Object valueAt(List<String> path) {
        Object current = root;
        for (String key : path) {
            if (!(current instanceof Map<?, ?> table)) {
                return null;
            }
            current = table.get(key);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    /** @return whether a proper prefix of {@code path} is present but is not a table */
    boolean hasNonTableSection(List<String> path) {
        Object current = root;
        for (String key : path.subList(0, path.size() - 1)) {
            current = ((Map<?, ?>) current).get(key);
            if (current == null) {
                return false;
            }
            if (!(current instanceof Map<?, ?>)) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, Object> copyTable(Map<String, ?> table) {
        Map<String, Object> copy = new LinkedHashMap<>();
        table.forEach((key, value) -> copy.put(Objects.requireNonNull(key, "key"), copyValue(value)));
        return Collections.unmodifiableMap(copy);
    }

    @SuppressWarnings("unchecked")
    private static Object copyValue(Object value) {
        Objects.requireNonNull(value, "persisted values must not be null");
        if (value instanceof Map<?, ?> table) {
            return copyTable((Map<String, ?>) table);
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            for (Object element : list) {
                copy.add(copyValue(element));
            }
            return Collections.unmodifiableList(copy);
        }
        return value;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof PersistedSettings that && root.equals(that.root);
    }

    @Override
    public int hashCode() {
        return root.hashCode();
    }

    /** Deliberately omits values so that configuration contents never reach diagnostics. */
    @Override
    public String toString() {
        return "PersistedSettings[" + root.size() + " top-level entries]";
    }
}
