package com.pidluzsnij.strategy.ui.input;

import java.util.Objects;

/**
 * Semantic activation of an interactive component: the component and the application-defined behavior
 * identifier from its definition. Application code decides what, if anything, the behavior does.
 */
public record UiActivation(String componentId, String behavior) {

    public UiActivation {
        Objects.requireNonNull(componentId, "componentId");
        Objects.requireNonNull(behavior, "behavior");
    }
}
