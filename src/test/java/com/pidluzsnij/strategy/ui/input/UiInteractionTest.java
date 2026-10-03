package com.pidluzsnij.strategy.ui.input;

import com.pidluzsnij.strategy.ui.definition.Bounds;
import com.pidluzsnij.strategy.ui.definition.VisualState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pointer interaction states and activation rules. */
class UiInteractionTest {

    /** Logical 1000×500 shown in a 2000×1000 framebuffer: scale 2, no unused area. */
    private static final int W = 2000;
    private static final int H = 1000;
    private final UiInteraction interaction = new UiInteraction(1000, 500, List.of(
            new UiInteraction.Target("start", new Bounds(100, 100, 200, 100), "test.start"),
            new UiInteraction.Target("overlap", new Bounds(250, 120, 200, 100), "test.overlap")));

    private static final double[] INSIDE = {400, 300};
    private static final double[] OUTSIDE = {1500, 900};

    private void move(double[] p) {
        interaction.pointerMoved(p[0], p[1], W, H);
    }

    private Optional<UiActivation> press(double[] p) {
        return interaction.primaryButton(true, p[0], p[1], W, H);
    }

    private Optional<UiActivation> release(double[] p) {
        return interaction.primaryButton(false, p[0], p[1], W, H);
    }

    @Test
    void hoverFollowsThePointer() {
        assertEquals(VisualState.NORMAL, interaction.state("start"));
        move(INSIDE);
        assertEquals(VisualState.HOVERED, interaction.state("start"));
        move(OUTSIDE);
        assertEquals(VisualState.NORMAL, interaction.state("start"));
        move(INSIDE);
        interaction.pointerLeft();
        assertEquals(VisualState.NORMAL, interaction.state("start"));
    }

    @Test
    void pressLeaveReenterReleaseActivatesOnce() {
        move(INSIDE);
        assertEquals(VisualState.HOVERED, interaction.state("start"));
        assertTrue(press(INSIDE).isEmpty());
        assertEquals(VisualState.PRESSED, interaction.state("start"));
        move(OUTSIDE);
        assertEquals(VisualState.NORMAL, interaction.state("start"));
        move(INSIDE);
        assertEquals(VisualState.PRESSED, interaction.state("start"));
        assertEquals(Optional.of(new UiActivation("start", "test.start")), release(INSIDE));
        assertEquals(VisualState.HOVERED, interaction.state("start"));
        assertTrue(release(INSIDE).isEmpty(), "a second release does not activate again");
    }

    @Test
    void releaseOutsideDoesNotActivate() {
        move(INSIDE);
        press(INSIDE);
        move(OUTSIDE);
        assertTrue(release(OUTSIDE).isEmpty());
        assertEquals(VisualState.NORMAL, interaction.state("start"));
    }

    @Test
    void pressBeginningOutsideDoesNotActivate() {
        move(OUTSIDE);
        press(OUTSIDE);
        move(INSIDE);
        assertEquals(VisualState.NORMAL, interaction.state("start"), "a foreign press neither hovers nor presses");
        assertTrue(release(INSIDE).isEmpty());
        assertEquals(VisualState.HOVERED, interaction.state("start"));
    }

    @Test
    void pressOnOneComponentDoesNotActivateAnother() {
        double[] onlyOverlap = {800, 400};
        move(INSIDE);
        press(INSIDE);
        move(onlyOverlap);
        assertEquals(VisualState.NORMAL, interaction.state("overlap"));
        assertTrue(release(onlyOverlap).isEmpty());
    }

    @Test
    void topmostComponentWinsWhereComponentsOverlap() {
        double[] both = {560, 260};
        move(both);
        assertEquals(VisualState.HOVERED, interaction.state("overlap"));
        assertEquals(VisualState.NORMAL, interaction.state("start"));
        press(both);
        assertEquals(Optional.of(new UiActivation("overlap", "test.overlap")), release(both));
    }

    @Test
    void pointerInUnusedFramebufferAreaNeverInteracts() {
        // Logical 1000×500 in a 3000×1000 framebuffer: scale 2, unused 500-pixel columns at each side.
        UiInteraction edge = new UiInteraction(1000, 500,
                List.of(new UiInteraction.Target("edge", new Bounds(-50, 0, 100, 100), "test.edge")));
        for (double[] unused : new double[][] {{450, 50}, {10, 10}, {2950, 990}, {499.9, 0}}) {
            edge.pointerMoved(unused[0], unused[1], 3000, 1000);
            assertEquals(VisualState.NORMAL, edge.state("edge"));
            assertTrue(edge.primaryButton(true, unused[0], unused[1], 3000, 1000).isEmpty());
            assertEquals(VisualState.NORMAL, edge.state("edge"));
            assertTrue(edge.primaryButton(false, unused[0], unused[1], 3000, 1000).isEmpty());
            assertEquals(VisualState.NORMAL, edge.state("edge"));
        }
        edge.pointerMoved(550, 50, 3000, 1000);
        assertEquals(VisualState.HOVERED, edge.state("edge"), "the part inside the logical area is interactive");
    }

    @Test
    void blockingComponentDrawnOverAButtonStopsHoverAndActivation() {
        // Logical 1000×500 in 2000×1000 (scale 2): the button covers x 200..600; the blocker covers x 400..800.
        UiInteraction covered = new UiInteraction(1000, 500, List.of(
                new UiInteraction.Target("button", new Bounds(100, 100, 200, 100), "test.start"),
                UiInteraction.Target.blocker("panel", new Bounds(200, 100, 200, 100))));
        double[] underPanel = {500, 300};
        double[] uncovered = {300, 300};

        covered.pointerMoved(underPanel[0], underPanel[1], W, H);
        assertEquals(VisualState.NORMAL, covered.state("button"));
        assertTrue(covered.primaryButton(true, underPanel[0], underPanel[1], W, H).isEmpty());
        assertEquals(VisualState.NORMAL, covered.state("button"));
        assertTrue(covered.primaryButton(false, underPanel[0], underPanel[1], W, H).isEmpty());

        covered.pointerMoved(uncovered[0], uncovered[1], W, H);
        assertEquals(VisualState.HOVERED, covered.state("button"), "the uncovered part stays interactive");
    }

    @Test
    void releaseOverABlockerDoesNotActivate() {
        UiInteraction covered = new UiInteraction(1000, 500, List.of(
                new UiInteraction.Target("button", new Bounds(100, 100, 200, 100), "test.start"),
                UiInteraction.Target.blocker("panel", new Bounds(200, 100, 200, 100))));
        covered.primaryButton(true, 300, 300, W, H);
        assertEquals(VisualState.PRESSED, covered.state("button"));
        covered.pointerMoved(500, 300, W, H);
        assertEquals(VisualState.NORMAL, covered.state("button"));
        assertTrue(covered.primaryButton(false, 500, 300, W, H).isEmpty());
    }

    @Test
    void blockerBelowAButtonDoesNotBlockIt() {
        UiInteraction below = new UiInteraction(1000, 500, List.of(
                UiInteraction.Target.blocker("background", new Bounds(0, 0, 1000, 500)),
                new UiInteraction.Target("button", new Bounds(100, 100, 200, 100), "test.start")));
        below.pointerMoved(300, 300, W, H);
        assertEquals(VisualState.HOVERED, below.state("button"));
    }

    // --- SPEC007: disabled components ---------------------------------------------------------

    @Test
    void disabledComponentStaysDisabledAndNeverActivates() {
        UiInteraction disabled = new UiInteraction(1000, 500, List.of(
                new UiInteraction.Target("start", new Bounds(100, 100, 200, 100), "test.start", false)));
        java.util.function.Consumer<double[]> move = p -> disabled.pointerMoved(p[0], p[1], W, H);

        assertEquals(VisualState.DISABLED, disabled.state("start"));
        move.accept(INSIDE);
        assertEquals(VisualState.DISABLED, disabled.state("start"));
        assertTrue(disabled.primaryButton(true, INSIDE[0], INSIDE[1], W, H).isEmpty());
        assertEquals(VisualState.DISABLED, disabled.state("start"));
        move.accept(OUTSIDE);
        assertEquals(VisualState.DISABLED, disabled.state("start"));
        move.accept(INSIDE);
        assertEquals(VisualState.DISABLED, disabled.state("start"));
        assertTrue(disabled.primaryButton(false, INSIDE[0], INSIDE[1], W, H).isEmpty(), "no activation");
        assertEquals(VisualState.DISABLED, disabled.state("start"));
        assertTrue(disabled.primaryButton(true, INSIDE[0], INSIDE[1], W, H).isEmpty());
        assertTrue(disabled.primaryButton(false, INSIDE[0], INSIDE[1], W, H).isEmpty(), "no activation on a plain click");
        disabled.pointerLeft();
        disabled.reset();
        assertEquals(VisualState.DISABLED, disabled.state("start"));
    }

    @Test
    void disabledComponentBlocksInteractiveComponentsBelowIt() {
        UiInteraction stacked = new UiInteraction(1000, 500, List.of(
                new UiInteraction.Target("below", new Bounds(100, 100, 200, 100), "test.below"),
                new UiInteraction.Target("above", new Bounds(100, 100, 200, 100), "test.above", false)));

        stacked.pointerMoved(INSIDE[0], INSIDE[1], W, H);
        assertEquals(VisualState.NORMAL, stacked.state("below"));
        stacked.primaryButton(true, INSIDE[0], INSIDE[1], W, H);
        assertEquals(VisualState.NORMAL, stacked.state("below"));
        assertTrue(stacked.primaryButton(false, INSIDE[0], INSIDE[1], W, H).isEmpty());
        assertEquals(VisualState.DISABLED, stacked.state("above"));
    }

    @Test
    void enabledTargetsBehaveAsBeforeNextToDisabledOnes() {
        UiInteraction mixed = new UiInteraction(1000, 500, List.of(
                new UiInteraction.Target("off", new Bounds(500, 100, 200, 100), "test.off", false),
                new UiInteraction.Target("start", new Bounds(100, 100, 200, 100), "test.start", true)));

        mixed.pointerMoved(INSIDE[0], INSIDE[1], W, H);
        assertEquals(VisualState.HOVERED, mixed.state("start"));
        mixed.primaryButton(true, INSIDE[0], INSIDE[1], W, H);
        assertEquals(VisualState.PRESSED, mixed.state("start"));
        assertEquals(Optional.of(new UiActivation("start", "test.start")),
                mixed.primaryButton(false, INSIDE[0], INSIDE[1], W, H));
        assertEquals(VisualState.DISABLED, mixed.state("off"));
    }
}
