package com.pidluzsnij.strategy.ui.resource;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A validated relative UI resource path such as {@code images/button.png}, confined to a UI resource root.
 * <p>
 * Paths consist of one or more {@code /}-separated segments of ASCII letters, digits, {@code _}, {@code -}
 * and {@code .}. Absolute paths, drive or device prefixes, backslashes, empty, {@code .} and {@code ..}
 * segments, segments ending in {@code .} and Windows reserved device names are rejected, so a valid path
 * can never address a file outside the root it is resolved against.
 */
public final class UiResourcePath {

    /** Maximum length of a path. */
    public static final int MAX_LENGTH = 256;

    private static final Pattern SEGMENT = Pattern.compile("[A-Za-z0-9_][A-Za-z0-9_.-]*");
    private static final Pattern RESERVED = Pattern.compile("(con|prn|aux|nul|com[0-9]|lpt[0-9])(\\..*)?");

    private final String path;
    private final List<String> segments;

    private UiResourcePath(String path) {
        this.path = path;
        this.segments = List.of(path.split("/", -1));
    }

    /**
     * @param path a relative path
     * @return the validated path
     * @throws IllegalArgumentException when the path is not a valid confined relative path; the message never
     *                                  quotes the rejected path
     */
    public static UiResourcePath of(String path) {
        Objects.requireNonNull(path, "path");
        String problem = problem(path);
        if (problem != null) {
            throw new IllegalArgumentException("invalid UI resource path: " + problem);
        }
        return new UiResourcePath(path);
    }

    /** @return whether {@code path} is a valid confined relative path */
    public static boolean isValid(String path) {
        return path != null && problem(path) == null;
    }

    private static String problem(String path) {
        if (path.isEmpty()) {
            return "the path is empty";
        }
        if (path.length() > MAX_LENGTH) {
            return "the path is longer than " + MAX_LENGTH + " characters";
        }
        if (path.startsWith("/") || path.startsWith("\\")) {
            return "absolute paths are not allowed";
        }
        if (path.indexOf(':') >= 0) {
            return "drive, device and stream prefixes are not allowed";
        }
        if (path.indexOf('\\') >= 0) {
            return "backslashes are not allowed";
        }
        for (String segment : path.split("/", -1)) {
            if (segment.isEmpty()) {
                return "empty path segments are not allowed";
            }
            if (segment.equals(".") || segment.equals("..")) {
                return "'.' and '..' segments are not allowed";
            }
            if (!SEGMENT.matcher(segment).matches()) {
                return "segments may contain only ASCII letters, digits, '_', '-' and '.'";
            }
            if (segment.endsWith(".")) {
                return "segments must not end with '.'";
            }
            if (RESERVED.matcher(segment.toLowerCase(Locale.ROOT)).matches()) {
                return "reserved device names are not allowed";
            }
        }
        return null;
    }

    /** @return the path's segments in order */
    public List<String> segments() {
        return segments;
    }

    /** @return the lower-case file extension including the dot, or an empty string */
    public String extension() {
        String last = segments.get(segments.size() - 1);
        int dot = last.lastIndexOf('.');
        return dot < 0 ? "" : last.substring(dot).toLowerCase(Locale.ROOT);
    }

    /** @return whether the path has one of {@code extensions} (lower case, including the dot) */
    public boolean hasExtension(Set<String> extensions) {
        return extensions.contains(extension());
    }

    /** @return the path in {@code /}-separated form */
    public String value() {
        return path;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof UiResourcePath that && path.equals(that.path);
    }

    @Override
    public int hashCode() {
        return path.hashCode();
    }

    @Override
    public String toString() {
        return path;
    }
}
