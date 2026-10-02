package com.pidluzsnij.strategy.ui.render;

import com.pidluzsnij.strategy.ui.asset.GlyphAtlas;

/** Graphics resources for drawing text from one glyph atlas. {@link #close()} is safe to repeat. */
public interface UiTextFace extends AutoCloseable {

    /** @return the glyph atlas the face draws from */
    GlyphAtlas atlas();

    @Override
    void close();
}
