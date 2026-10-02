package com.pidluzsnij.strategy.ui.render;

import com.pidluzsnij.strategy.ui.asset.GlyphAtlas;
import com.pidluzsnij.strategy.ui.definition.TextAlign;
import com.pidluzsnij.strategy.ui.layout.ScreenRect;

import java.util.ArrayList;
import java.util.List;

/** Places the glyphs of a single-line text run within a rectangle, independent of the graphics API. */
public final class TextPlacement {

    /** A glyph quad in framebuffer pixels with its atlas texture coordinates (0–1, top-left origin). */
    public record GlyphQuad(int codePoint, float x, float y, float width, float height,
                            float u0, float v0, float u1, float v1) {
    }

    private TextPlacement() {
    }

    public static List<GlyphQuad> place(GlyphAtlas atlas, String text, ScreenRect rect, TextAlign align,
                                        float pixelSize) {
        float scale = pixelSize / atlas.pixelHeight();
        GlyphAtlas.Run run = atlas.layout(text);
        float runWidth = run.width() * scale;
        float startX = switch (align) {
            case LEFT -> rect.x();
            case CENTER -> rect.x() + (rect.width() - runWidth) / 2f;
            case RIGHT -> rect.x() + rect.width() - runWidth;
        };
        // Center the font's ascent-to-descent band vertically within the rectangle.
        float baseline = rect.y() + rect.height() / 2f + (atlas.ascent() + atlas.descent()) * scale / 2f;
        List<GlyphQuad> quads = new ArrayList<>(run.glyphs().size());
        for (GlyphAtlas.PlacedGlyph placed : run.glyphs()) {
            GlyphAtlas.Glyph glyph = placed.glyph();
            if (glyph.width() == 0 || glyph.height() == 0) {
                continue;
            }
            float x = Math.round(startX + placed.x() * scale);
            float y = Math.round(baseline + placed.y() * scale);
            quads.add(new GlyphQuad(glyph.codePoint(), x, y, glyph.width() * scale, glyph.height() * scale,
                    (float) glyph.atlasX() / atlas.width(), (float) glyph.atlasY() / atlas.height(),
                    (float) (glyph.atlasX() + glyph.width()) / atlas.width(),
                    (float) (glyph.atlasY() + glyph.height()) / atlas.height()));
        }
        return quads;
    }
}
