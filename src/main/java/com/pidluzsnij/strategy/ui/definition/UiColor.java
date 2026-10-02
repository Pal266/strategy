package com.pidluzsnij.strategy.ui.definition;

import java.util.regex.Pattern;

/** An sRGB color with straight alpha, each channel 0–255. */
public record UiColor(int red, int green, int blue, int alpha) {

    private static final Pattern HEX = Pattern.compile("#([0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})");

    public static final UiColor WHITE = new UiColor(255, 255, 255, 255);

    public UiColor {
        for (int channel : new int[] {red, green, blue, alpha}) {
            if (channel < 0 || channel > 255) {
                throw new IllegalArgumentException("color channels must be within 0-255");
            }
        }
    }

    /** @return whether {@code text} is {@code #RRGGBB} or {@code #RRGGBBAA} */
    public static boolean isValid(String text) {
        return HEX.matcher(text).matches();
    }

    /** Parses {@code #RRGGBB} (opaque) or {@code #RRGGBBAA}. */
    public static UiColor parse(String text) {
        if (!isValid(text)) {
            throw new IllegalArgumentException("colors must be written as #RRGGBB or #RRGGBBAA");
        }
        int alpha = text.length() == 9 ? Integer.parseInt(text.substring(7, 9), 16) : 255;
        return new UiColor(Integer.parseInt(text.substring(1, 3), 16), Integer.parseInt(text.substring(3, 5), 16),
                Integer.parseInt(text.substring(5, 7), 16), alpha);
    }
}
