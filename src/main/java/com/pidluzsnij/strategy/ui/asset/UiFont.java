package com.pidluzsnij.strategy.ui.asset;

import com.pidluzsnij.strategy.ui.UiException;
import com.pidluzsnij.strategy.ui.resource.ResolvedUiResource;
import org.lwjgl.stb.STBTTFontinfo;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.lwjgl.stb.STBTruetype.stbtt_FindGlyphIndex;
import static org.lwjgl.stb.STBTruetype.stbtt_FreeBitmap;
import static org.lwjgl.stb.STBTruetype.stbtt_GetCodepointBitmap;
import static org.lwjgl.stb.STBTruetype.stbtt_GetCodepointHMetrics;
import static org.lwjgl.stb.STBTruetype.stbtt_GetFontVMetrics;
import static org.lwjgl.stb.STBTruetype.stbtt_InitFont;
import static org.lwjgl.stb.STBTruetype.stbtt_ScaleForPixelHeight;

/**
 * A TrueType font loaded with LWJGL STB from a UI resource. The font owns native memory (the font data and
 * the STB font information) until {@link #close()}, which is safe to call more than once.
 * <p>
 * Text is addressed by Unicode code point, so any character the font maps can be rasterized.
 */
public final class UiFont implements AutoCloseable {

    public static final String STAGE = "load UI font";

    /** Tables stb_truetype needs to rasterize TrueType outlines. */
    private static final Set<String> REQUIRED_TABLES = Set.of("cmap", "head", "hhea", "hmtx", "loca", "glyf", "maxp");
    private static final int MAX_TABLES = 512;

    private final String name;
    private ByteBuffer data;
    private STBTTFontinfo info;

    private UiFont(String name, ByteBuffer data, STBTTFontinfo info) {
        this.name = name;
        this.data = data;
        this.info = info;
    }

    /**
     * @throws UiException when the resource is not a single TrueType font that STB can use; no native memory
     *                     remains allocated in that case
     */
    public static UiFont load(ResolvedUiResource resource) throws UiException {
        ByteBuffer bytes = resource.buffer();
        String problem = structuralProblem(bytes);
        if (problem != null) {
            throw new UiException(STAGE, resource.name(), problem);
        }
        ByteBuffer data = MemoryUtil.memAlloc(bytes.remaining());
        STBTTFontinfo info = null;
        boolean loaded = false;
        try {
            data.put(bytes).flip();
            info = STBTTFontinfo.malloc();
            if (!stbtt_InitFont(info, data)) {
                throw new UiException(STAGE, resource.name(), "the TrueType font could not be initialized");
            }
            loaded = true;
            return new UiFont(resource.name(), data, info);
        } finally {
            if (!loaded) {
                if (info != null) {
                    info.free();
                }
                MemoryUtil.memFree(data);
            }
        }
    }

    /**
     * Validates the TrueType table directory before the data reaches the native rasterizer.
     *
     * @return a safe description of the problem, or {@code null} when the structure is acceptable
     */
    static String structuralProblem(ByteBuffer bytes) {
        int size = bytes.remaining();
        if (size < 12) {
            return "the resource is too short to be a TrueType font";
        }
        int version = bytes.getInt(0);
        if (version == 0x74746366) { // 'ttcf'
            return "font collections are not supported";
        }
        if (version != 0x00010000 && version != 0x74727565) { // 1.0 or 'true'
            return "the resource is not a TrueType font";
        }
        int tables = Short.toUnsignedInt(bytes.getShort(4));
        if (tables == 0 || tables > MAX_TABLES) {
            return "the TrueType table directory is malformed";
        }
        if (12L + tables * 16L > size) {
            return "the TrueType table directory is truncated";
        }
        List<String> present = new ArrayList<>();
        for (int i = 0; i < tables; i++) {
            int record = 12 + i * 16;
            byte[] tagBytes = new byte[4];
            bytes.get(record, tagBytes);
            String tag = new String(tagBytes, java.nio.charset.StandardCharsets.ISO_8859_1);
            long offset = Integer.toUnsignedLong(bytes.getInt(record + 8));
            long length = Integer.toUnsignedLong(bytes.getInt(record + 12));
            if (offset + length > size) {
                return "a TrueType table lies outside the font data";
            }
            present.add(tag);
        }
        for (String table : REQUIRED_TABLES) {
            if (!present.contains(table)) {
                return "the TrueType font lacks a required table";
            }
        }
        return null;
    }

    /** @return the resource name of the font, for diagnostics */
    public String name() {
        return name;
    }

    /** @return the font's glyph index for {@code codePoint}, or 0 when the font does not map it */
    public int glyphIndex(int codePoint) {
        return stbtt_FindGlyphIndex(info(), codePoint);
    }

    /** @return whether the font maps {@code codePoint} to a glyph of its own */
    public boolean hasGlyph(int codePoint) {
        return glyphIndex(codePoint) != 0;
    }

    /**
     * Rasterizes the distinct code points of {@code codePoints} at {@code pixelHeight} into an atlas.
     *
     * @throws UiException when the glyphs do not fit the largest supported atlas
     */
    public GlyphAtlas rasterize(int pixelHeight, int[] codePoints) throws UiException {
        if (pixelHeight <= 0) {
            throw new IllegalArgumentException("pixel height must be positive");
        }
        STBTTFontinfo font = info();
        int[] distinct = Arrays.stream(codePoints).distinct().sorted().toArray();
        float scale = stbtt_ScaleForPixelHeight(font, pixelHeight);
        List<GlyphAtlas.Bitmap> bitmaps = new ArrayList<>(distinct.length);
        int ascent;
        int descent;
        int lineGap;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer a = stack.mallocInt(1);
            IntBuffer d = stack.mallocInt(1);
            IntBuffer g = stack.mallocInt(1);
            stbtt_GetFontVMetrics(font, a, d, g);
            ascent = a.get(0);
            descent = d.get(0);
            lineGap = g.get(0);
            IntBuffer width = stack.mallocInt(1);
            IntBuffer height = stack.mallocInt(1);
            IntBuffer xoff = stack.mallocInt(1);
            IntBuffer yoff = stack.mallocInt(1);
            IntBuffer advance = stack.mallocInt(1);
            IntBuffer bearing = stack.mallocInt(1);
            for (int codePoint : distinct) {
                stbtt_GetCodepointHMetrics(font, codePoint, advance, bearing);
                ByteBuffer bitmap = stbtt_GetCodepointBitmap(font, 0, scale, codePoint, width, height, xoff, yoff);
                byte[] alpha = new byte[0];
                int w = 0;
                int h = 0;
                if (bitmap != null) {
                    try {
                        w = width.get(0);
                        h = height.get(0);
                        alpha = new byte[w * h];
                        bitmap.get(0, alpha);
                    } finally {
                        stbtt_FreeBitmap(bitmap);
                    }
                }
                bitmaps.add(new GlyphAtlas.Bitmap(codePoint, glyphIndex(codePoint), w, h, xoff.get(0), yoff.get(0),
                        advance.get(0) * scale, alpha));
            }
        }
        return GlyphAtlas.pack(name, pixelHeight, ascent * scale, descent * scale, lineGap * scale, bitmaps);
    }

    private STBTTFontinfo info() {
        if (info == null) {
            throw new IllegalStateException("the font has been released");
        }
        return info;
    }

    /** @return whether the font's native memory has been released */
    public boolean isReleased() {
        return info == null && data == null;
    }

    /** Releases the font's native memory; later calls do nothing. */
    @Override
    public void close() {
        STBTTFontinfo releasedInfo = info;
        ByteBuffer releasedData = data;
        info = null;
        data = null;
        try {
            if (releasedInfo != null) {
                releasedInfo.free();
            }
        } finally {
            if (releasedData != null) {
                MemoryUtil.memFree(releasedData);
            }
        }
    }
}
