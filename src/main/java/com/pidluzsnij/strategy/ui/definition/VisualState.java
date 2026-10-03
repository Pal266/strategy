package com.pidluzsnij.strategy.ui.definition;

import java.util.Locale;

/**
 * Visual states of an interactive component: the interaction-dependent states of an enabled component and the
 * state of a disabled one, which never becomes hovered or pressed.
 */
public enum VisualState {
    NORMAL, HOVERED, PRESSED, DISABLED;

    /** @return the state's name in UI definitions */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
