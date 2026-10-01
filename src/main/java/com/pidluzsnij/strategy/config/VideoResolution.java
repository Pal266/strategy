package com.pidluzsnij.strategy.config;

/**
 * Configured application resolution: one logical setting that is either automatic
 * ({@code auto} for both width and height) or explicit (a width and a height in pixels).
 * <p>
 * Automatic selection is never exposed as numeric dimensions; resolving it to concrete
 * dimensions is a windowing concern outside the settings model.
 */
public sealed interface VideoResolution {

    /** Resolution chosen automatically when the application starts. */
    Automatic AUTOMATIC = new Automatic();

    /** @return an explicit resolution of {@code width}×{@code height} pixels */
    static Explicit of(int width, int height) {
        return new Explicit(width, height);
    }

    /** @return whether this is a valid configured resolution */
    boolean isValid();

    /** Both width and height are {@code auto}. */
    final class Automatic implements VideoResolution {

        private Automatic() {
        }

        @Override
        public boolean isValid() {
            return true;
        }

        @Override
        public String toString() {
            return "auto";
        }
    }

    /**
     * Both width and height are configured numerically.
     *
     * @param width  width in pixels; valid when positive
     * @param height height in pixels; valid when positive
     */
    record Explicit(int width, int height) implements VideoResolution {

        @Override
        public boolean isValid() {
            return width > 0 && height > 0;
        }

        @Override
        public String toString() {
            return width + "×" + height;
        }
    }
}
