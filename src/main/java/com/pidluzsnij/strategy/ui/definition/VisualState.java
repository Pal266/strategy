package com.pidluzsnij.strategy.ui.definition;

import java.util.Locale;

/** Interaction-dependent visual states of an interactive component. */
public enum VisualState {
    NORMAL, HOVERED, PRESSED;

    /** @return the state's name in UI definitions */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
