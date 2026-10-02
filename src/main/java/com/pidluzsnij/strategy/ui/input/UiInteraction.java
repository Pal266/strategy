package com.pidluzsnij.strategy.ui.input;

import com.pidluzsnij.strategy.ui.definition.Bounds;
import com.pidluzsnij.strategy.ui.definition.VisualState;
import com.pidluzsnij.strategy.ui.layout.LayoutTransform;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Pointer interaction state of the interactive components of one UI.
 * <ul>
 *     <li>Without a held primary button, the topmost interactive component under the pointer is hovered,
 *     unless a blocking component is drawn over it at that point.</li>
 *     <li>A press that begins on a component captures it: the component is pressed while the pointer is
 *     inside it and normal while outside; releasing inside it produces one activation.</li>
 *     <li>A press that begins on no component captures nothing: no component is hovered or pressed until
 *     the button is released, and the release activates nothing.</li>
 *     <li>Points outside the logical UI area never hit a component.</li>
 * </ul>
 * Hit testing uses {@link LayoutTransform#hits}, the transform also used for rendering.
 */
public final class UiInteraction {

    /**
     * A component taking part in hit testing: an interactive component with its semantic behavior identifier,
     * or a blocking component ({@code behavior} {@code null}) that stops pointer input from reaching
     * interactive components below it.
     */
    public record Target(String id, Bounds bounds, String behavior) {
        public Target {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(bounds, "bounds");
        }

        /** @return a target that blocks pointer input without being interactive */
        public static Target blocker(String id, Bounds bounds) {
            return new Target(id, bounds, null);
        }

        public boolean interactive() {
            return behavior != null;
        }
    }

    private final int logicalWidth;
    private final int logicalHeight;
    /** Interactive and blocking components in rendering order; later targets are on top. */
    private final List<Target> targets;

    private Target hovered;
    private boolean held;
    private Target captured;

    public UiInteraction(int logicalWidth, int logicalHeight, List<Target> targets) {
        this.logicalWidth = logicalWidth;
        this.logicalHeight = logicalHeight;
        this.targets = List.copyOf(targets);
    }

    public void pointerMoved(double x, double y, int framebufferWidth, int framebufferHeight) {
        hovered = hit(x, y, framebufferWidth, framebufferHeight);
    }

    public void pointerLeft() {
        hovered = null;
    }

    /** Forgets the pointer position and any press in progress, as when the UI is shown or hidden. */
    public void reset() {
        hovered = null;
        held = false;
        captured = null;
    }

    /** @return the activation produced by this event, if any */
    public Optional<UiActivation> primaryButton(boolean pressed, double x, double y,
                                                int framebufferWidth, int framebufferHeight) {
        hovered = hit(x, y, framebufferWidth, framebufferHeight);
        if (pressed) {
            if (!held) {
                held = true;
                captured = hovered;
            }
            return Optional.empty();
        }
        if (!held) {
            return Optional.empty();
        }
        Target activated = captured != null && captured == hovered ? captured : null;
        held = false;
        captured = null;
        return activated == null
                ? Optional.empty()
                : Optional.of(new UiActivation(activated.id(), activated.behavior()));
    }

    /** @return the current visual state of the component {@code id} */
    public VisualState state(String id) {
        if (held) {
            return captured != null && captured.id().equals(id) && captured == hovered
                    ? VisualState.PRESSED
                    : VisualState.NORMAL;
        }
        return hovered != null && hovered.id().equals(id) ? VisualState.HOVERED : VisualState.NORMAL;
    }

    private Target hit(double x, double y, int framebufferWidth, int framebufferHeight) {
        LayoutTransform transform = LayoutTransform.fit(logicalWidth, logicalHeight, framebufferWidth, framebufferHeight);
        for (int i = targets.size() - 1; i >= 0; i--) {
            Target target = targets.get(i);
            if (transform.hits(target.bounds(), x, y)) {
                // The topmost hit decides: an interactive component, or a blocker hiding what lies below.
                return target.interactive() ? target : null;
            }
        }
        return null;
    }
}
