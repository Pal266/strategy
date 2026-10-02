package com.pidluzsnij.strategy.ui.asset;

import com.pidluzsnij.strategy.ui.UiException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Glyphs of one font at one pixel height, rasterized by STB and packed into a single coverage bitmap
 * (one byte per pixel, rows from top to bottom). Held on the Java heap.
 */
public final class GlyphAtlas {

    /** Largest atlas width and height. */
    public static final int MAX_SIZE = 4096;
    private static final int PADDING = 1;

    /** A rasterized glyph before packing. */
    record Bitmap(int codePoint, int glyphIndex, int width, int height, int xOffset, int yOffset, float advance,
                  byte[] alpha) {
    }

    /**
     * A packed glyph: its atlas rectangle, the offset of that rectangle from the pen position on the baseline,
     * and the horizontal advance, all in pixels.
     */
    public record Glyph(int codePoint, int glyphIndex, int atlasX, int atlasY, int width, int height,
                        int xOffset, int yOffset, float advance) {
    }

    /** A glyph positioned for a text run, relative to the run's start on the baseline. */
    public record PlacedGlyph(Glyph glyph, float x, float y) {
    }

    /** A laid-out text run. */
    public record Run(List<PlacedGlyph> glyphs, float width) {
    }

    private final String fontName;
    private final int pixelHeight;
    private final float ascent;
    private final float descent;
    private final float lineGap;
    private final int width;
    private final int height;
    private final byte[] coverage;
    private final Map<Integer, Glyph> glyphs;

    private GlyphAtlas(String fontName, int pixelHeight, float ascent, float descent, float lineGap, int width,
                       int height, byte[] coverage, Map<Integer, Glyph> glyphs) {
        this.fontName = fontName;
        this.pixelHeight = pixelHeight;
        this.ascent = ascent;
        this.descent = descent;
        this.lineGap = lineGap;
        this.width = width;
        this.height = height;
        this.coverage = coverage;
        this.glyphs = Map.copyOf(glyphs);
    }

    static GlyphAtlas pack(String fontName, int pixelHeight, float ascent, float descent, float lineGap,
                           List<Bitmap> bitmaps) throws UiException {
        List<Bitmap> order = new ArrayList<>(bitmaps);
        order.sort(Comparator.comparingInt(Bitmap::height).reversed().thenComparingInt(Bitmap::codePoint));
        int atlasWidth = 256;
        long area = 0;
        int widest = 0;
        for (Bitmap bitmap : order) {
            area += (long) (bitmap.width() + PADDING) * (bitmap.height() + PADDING);
            widest = Math.max(widest, bitmap.width() + 2 * PADDING);
        }
        while (atlasWidth < MAX_SIZE && ((long) atlasWidth * atlasWidth < area * 2 || atlasWidth < widest)) {
            atlasWidth *= 2;
        }
        if (widest > atlasWidth) {
            throw new UiException(UiFont.STAGE, fontName, "the glyphs do not fit into a " + MAX_SIZE + " pixel atlas");
        }
        Map<Integer, Glyph> placed = new HashMap<>();
        int x = PADDING;
        int y = PADDING;
        int rowHeight = 0;
        for (Bitmap bitmap : order) {
            if (x + bitmap.width() + PADDING > atlasWidth) {
                x = PADDING;
                y += rowHeight + PADDING;
                rowHeight = 0;
            }
            placed.put(bitmap.codePoint(), new Glyph(bitmap.codePoint(), bitmap.glyphIndex(), x, y, bitmap.width(),
                    bitmap.height(), bitmap.xOffset(), bitmap.yOffset(), bitmap.advance()));
            x += bitmap.width() + PADDING;
            rowHeight = Math.max(rowHeight, bitmap.height());
        }
        int atlasHeight = 1;
        while (atlasHeight < y + rowHeight + PADDING) {
            atlasHeight *= 2;
        }
        if (atlasHeight > MAX_SIZE) {
            throw new UiException(UiFont.STAGE, fontName, "the glyphs do not fit into a " + MAX_SIZE + " pixel atlas");
        }
        byte[] coverage = new byte[atlasWidth * atlasHeight];
        for (Bitmap bitmap : order) {
            Glyph glyph = placed.get(bitmap.codePoint());
            for (int row = 0; row < bitmap.height(); row++) {
                System.arraycopy(bitmap.alpha(), row * bitmap.width(), coverage,
                        (glyph.atlasY() + row) * atlasWidth + glyph.atlasX(), bitmap.width());
            }
        }
        return new GlyphAtlas(fontName, pixelHeight, ascent, descent, lineGap, atlasWidth, atlasHeight, coverage, placed);
    }

    /**
     * Lays out {@code text} code point by code point on one line. Code points that were not rasterized into
     * this atlas are skipped; the text itself is never altered.
     */
    public Run layout(String text) {
        List<PlacedGlyph> run = new ArrayList<>();
        float pen = 0;
        int[] codePoints = text.codePoints().toArray();
        for (int codePoint : codePoints) {
            Glyph glyph = glyphs.get(codePoint);
            if (glyph == null) {
                continue;
            }
            run.add(new PlacedGlyph(glyph, pen + glyph.xOffset(), glyph.yOffset()));
            pen += glyph.advance();
        }
        return new Run(Collections.unmodifiableList(run), pen);
    }

    /** @return the packed glyph for {@code codePoint}, or {@code null} when it was not rasterized */
    public Glyph glyph(int codePoint) {
        return glyphs.get(codePoint);
    }

    /** @return the rasterized code points */
    public java.util.Set<Integer> codePoints() {
        return glyphs.keySet();
    }

    public String fontName() {
        return fontName;
    }

    public int pixelHeight() {
        return pixelHeight;
    }

    /** @return the font ascent in pixels above the baseline */
    public float ascent() {
        return ascent;
    }

    /** @return the font descent in pixels (negative: below the baseline) */
    public float descent() {
        return descent;
    }

    public float lineGap() {
        return lineGap;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    /** @return a copy of the coverage bitmap */
    public byte[] coverage() {
        return coverage.clone();
    }

    /** @return the number of covered (nonzero) pixels of {@code glyph} */
    public int coveredPixels(Glyph glyph) {
        int covered = 0;
        for (int row = 0; row < glyph.height(); row++) {
            for (int column = 0; column < glyph.width(); column++) {
                if (coverage[(glyph.atlasY() + row) * width + glyph.atlasX() + column] != 0) {
                    covered++;
                }
            }
        }
        return covered;
    }

}
