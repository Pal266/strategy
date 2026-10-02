package com.pidluzsnij.strategy.ui.render;

/** A graphics texture created from a UI image. {@link #close()} releases it and is safe to repeat. */
public interface UiTexture extends AutoCloseable {

    /** @return the resource name of the image, for diagnostics */
    String name();

    int width();

    int height();

    @Override
    void close();
}
