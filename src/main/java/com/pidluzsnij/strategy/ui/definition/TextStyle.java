package com.pidluzsnij.strategy.ui.definition;

import com.pidluzsnij.strategy.ui.resource.UiResourcePath;

import java.util.Objects;

/**
 * Text of a component: the localization key whose application-supplied value is displayed, the font resource,
 * the font size in logical units, the color and the horizontal alignment.
 */
public record TextStyle(String localizationKey, UiResourcePath font, int size, UiColor color, TextAlign align) {

    public TextStyle {
        Objects.requireNonNull(localizationKey, "localizationKey");
        Objects.requireNonNull(font, "font");
        Objects.requireNonNull(color, "color");
        Objects.requireNonNull(align, "align");
        if (size <= 0) {
            throw new IllegalArgumentException("font size must be positive");
        }
    }
}
