package com.pidluzsnij.strategy.ui.layout;

import com.pidluzsnij.strategy.ui.definition.Bounds;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Uniform, centered, aspect-preserving mapping of logical UI coordinates to the framebuffer. */
class LayoutTransformTest {

    private static final Bounds COMPONENT = new Bounds(160, 90, 320, 180);

    @Test
    void sameAspectRatioFillsTheFramebuffer() {
        LayoutTransform transform = LayoutTransform.fit(1600, 900, 3200, 1800);

        assertEquals(2f, transform.scale());
        assertEquals(new ScreenRect(0, 0, 3200, 1800), transform.logicalArea());
        assertEquals(new ScreenRect(320, 180, 640, 360), transform.toFramebuffer(COMPONENT));
    }

    @Test
    void widerFramebufferLeavesCenteredUnusedColumns() {
        LayoutTransform transform = LayoutTransform.fit(1600, 900, 2400, 900);

        assertEquals(1f, transform.scale());
        assertEquals(new ScreenRect(400, 0, 1600, 900), transform.logicalArea());
        ScreenRect rect = transform.toFramebuffer(COMPONENT);
        assertEquals(new ScreenRect(560, 90, 320, 180), rect);
        assertEquals(rect.width() / rect.height(), 320f / 180f, 1e-6, "proportions are preserved");
    }

    @Test
    void tallerFramebufferLeavesCenteredUnusedRows() {
        LayoutTransform transform = LayoutTransform.fit(1600, 900, 800, 1000);

        assertEquals(0.5f, transform.scale());
        assertEquals(new ScreenRect(0, 275, 800, 450), transform.logicalArea());
        assertEquals(new ScreenRect(80, 320, 160, 90), transform.toFramebuffer(COMPONENT));
    }

    @Test
    void emptyFramebufferShowsNothing() {
        LayoutTransform transform = LayoutTransform.fit(1600, 900, 0, 0);
        assertEquals(0f, transform.scale());
        assertFalse(transform.hits(COMPONENT, 0, 0));
    }

    @ParameterizedTest
    @CsvSource({"1600,900", "1920,1080", "2400,900", "800,1000", "1280,1024", "3840,1600", "1366,768"})
    void hitTestingAgreesWithRenderedBoundsAtEveryResolution(int width, int height) {
        LayoutTransform transform = LayoutTransform.fit(1600, 900, width, height);
        ScreenRect rendered = transform.toFramebuffer(COMPONENT);
        ScreenRect area = transform.logicalArea();

        // Inside, on the inclusive top-left edges, and just inside the exclusive bottom-right edges.
        assertTrue(transform.hits(COMPONENT, rendered.x() + rendered.width() / 2, rendered.y() + rendered.height() / 2));
        assertTrue(transform.hits(COMPONENT, rendered.x(), rendered.y()));
        assertTrue(transform.hits(COMPONENT, rendered.x() + rendered.width() - 0.01, rendered.y() + rendered.height() - 0.01));
        // Just outside every edge.
        assertFalse(transform.hits(COMPONENT, rendered.x() - 0.01, rendered.y() + 1));
        assertFalse(transform.hits(COMPONENT, rendered.x() + 1, rendered.y() - 0.01));
        assertFalse(transform.hits(COMPONENT, rendered.x() + rendered.width(), rendered.y() + 1));
        assertFalse(transform.hits(COMPONENT, rendered.x() + 1, rendered.y() + rendered.height()));
        // Every sampled point hits exactly when it lies in the rendered rectangle.
        for (int x = 0; x < width; x += 7) {
            for (int y = 0; y < height; y += 7) {
                assertEquals(rendered.contains(x, y) && area.contains(x, y), transform.hits(COMPONENT, x, y));
            }
        }
    }

    @Test
    void componentsExtendingIntoTheUnusedAreaAreNotHitThere() {
        Bounds edge = new Bounds(-100, 0, 200, 100);
        LayoutTransform transform = LayoutTransform.fit(1600, 900, 2400, 900);
        // Rendered from x=300, but the logical area starts at x=400.
        assertFalse(transform.hits(edge, 350, 50));
        assertTrue(transform.hits(edge, 450, 50));
    }
}
