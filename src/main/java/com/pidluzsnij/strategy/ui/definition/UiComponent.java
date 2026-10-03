package com.pidluzsnij.strategy.ui.definition;

import com.pidluzsnij.strategy.ui.resource.UiResourcePath;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** A validated component of a UI definition. Components are pure data: they carry no executable behavior. */
public sealed interface UiComponent {

    /** @return the component identifier, unique within its definition */
    String id();

    /** @return the component's bounds in logical coordinates */
    Bounds bounds();

    /**
     * @return whether the component stops pointer input from reaching interactive components drawn below it;
     * interactive components always do
     */
    boolean blocking();

    /**
     * An image stretched over the component bounds. A non-blocking image is a decoration that pointer input
     * passes through.
     */
    record Image(String id, Bounds bounds, UiResourcePath image, boolean blocking) implements UiComponent {
        public Image {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(bounds, "bounds");
            Objects.requireNonNull(image, "image");
        }
    }

    /** Localized text within the component bounds. Non-blocking text lets pointer input pass through. */
    record Text(String id, Bounds bounds, TextStyle text, boolean blocking) implements UiComponent {
        public Text {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(bounds, "bounds");
            Objects.requireNonNull(text, "text");
        }
    }

    /**
     * An interactive component: one image per visual state, an optional label, and the identifier of the
     * application-defined semantic behavior that application code performs when the component is activated.
     * A disabled button is always drawn in its {@linkplain VisualState#DISABLED disabled} state, is never
     * hovered or pressed and never produces an activation; it still blocks pointer input below it.
     */
    record Button(String id, Bounds bounds, String behavior, boolean enabled, Map<VisualState, UiResourcePath> states,
                  Optional<TextStyle> label) implements UiComponent {
        public Button {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(bounds, "bounds");
            Objects.requireNonNull(behavior, "behavior");
            Objects.requireNonNull(label, "label");
            EnumMap<VisualState, UiResourcePath> copy = new EnumMap<>(VisualState.class);
            copy.putAll(states);
            for (VisualState state : VisualState.values()) {
                Objects.requireNonNull(copy.get(state), "state " + state.id());
            }
            states = java.util.Collections.unmodifiableMap(copy);
        }

        @Override
        public boolean blocking() {
            return true;
        }

        /** @return the image resource for {@code state} */
        public UiResourcePath image(VisualState state) {
            return states.get(state);
        }
    }
}
