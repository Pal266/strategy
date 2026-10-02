package com.pidluzsnij.strategy.ui.asset;

import com.pidluzsnij.strategy.testsupport.UiFixtures;
import com.pidluzsnij.strategy.ui.UiException;
import com.pidluzsnij.strategy.ui.resource.ResolvedUiResource;
import com.pidluzsnij.strategy.ui.resource.UiResourceOrigin;
import com.pidluzsnij.strategy.ui.resource.UiResourcePath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** TrueType loading and Unicode glyph rasterization through LWJGL STB. */
class UiFontTest {

    private static ResolvedUiResource resource(byte[] bytes) {
        return new ResolvedUiResource(UiResourcePath.of("fonts/test.ttf"), UiResourceOrigin.BUNDLED, bytes);
    }

    private static List<String> samples() throws Exception {
        List<String> samples = new ArrayList<>(UiFixtures.LANGUAGE_SAMPLES);
        // The window titles actually supplied by the four bundled localization files.
        for (String file : List.of("english", "ukrainian", "czech", "hungarian")) {
            try (InputStream in = UiFontTest.class.getClassLoader().getResourceAsStream("localization/" + file + ".properties")) {
                Properties properties = new Properties();
                properties.load(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
                samples.add(properties.getProperty("application.window.title"));
            }
        }
        return samples;
    }

    @Test
    void glyphsForTheFourDefaultLanguagesAreAddressableAndRasterized() throws Exception {
        try (UiFont font = UiFont.load(resource(UiFixtures.font(UiFixtures.FONT_A)))) {
            for (String text : samples()) {
                int[] codePoints = text.codePoints().toArray();
                for (int codePoint : codePoints) {
                    assertTrue(font.hasGlyph(codePoint),
                            "glyph for U+" + Integer.toHexString(codePoint).toUpperCase() + " in " + text);
                }
                GlyphAtlas atlas = font.rasterize(32, codePoints);
                for (int codePoint : codePoints) {
                    GlyphAtlas.Glyph glyph = atlas.glyph(codePoint);
                    assertNotNull(glyph);
                    assertEquals(font.glyphIndex(codePoint), glyph.glyphIndex(), "no replacement glyph is used");
                    if (!Character.isWhitespace(codePoint)) {
                        assertTrue(atlas.coveredPixels(glyph) > 0, "rasterized U+" + Integer.toHexString(codePoint));
                    }
                }
                GlyphAtlas.Run run = atlas.layout(text);
                String laidOut = run.glyphs().stream()
                        .map(g -> new String(Character.toChars(g.glyph().codePoint())))
                        .collect(Collectors.joining());
                assertEquals(text, laidOut, "text is laid out code point by code point, unchanged");
                assertTrue(run.width() > 0);
            }
        }
    }

    @Test
    void supplementaryCodePointsAreHandledAsSingleCharacters() throws Exception {
        try (UiFont font = UiFont.load(resource(UiFixtures.font(UiFixtures.FONT_A)))) {
            String text = "a😀b";
            GlyphAtlas atlas = font.rasterize(16, text.codePoints().toArray());
            assertNotNull(atlas.glyph(0x1F600));
            assertEquals(0, atlas.glyph(0x1F600).glyphIndex(), "the test font does not map this code point");
            assertEquals(3, atlas.layout(text).glyphs().size());
        }
    }

    @Test
    void pixelHeightControlsGlyphSize() throws Exception {
        try (UiFont font = UiFont.load(resource(UiFixtures.font(UiFixtures.FONT_A)))) {
            GlyphAtlas small = font.rasterize(16, new int[] {'M'});
            GlyphAtlas large = font.rasterize(64, new int[] {'M'});
            assertTrue(large.glyph('M').height() > small.glyph('M').height() * 3);
            assertEquals(64, large.pixelHeight());
        }
    }

    @Test
    void differentFontsProduceDifferentGlyphs() throws Exception {
        try (UiFont a = UiFont.load(resource(UiFixtures.font(UiFixtures.FONT_A)));
             UiFont b = UiFont.load(resource(UiFixtures.font(UiFixtures.FONT_B)))) {
            assertTrue(b.rasterize(32, new int[] {'H'}).glyph('H').advance()
                    > a.rasterize(32, new int[] {'H'}).glyph('H').advance());
        }
    }

    @Test
    void releaseIsIdempotent() throws Exception {
        UiFont font = UiFont.load(resource(UiFixtures.font(UiFixtures.FONT_A)));
        assertFalse(font.isReleased());
        font.close();
        assertTrue(font.isReleased());
        font.close();
        assertTrue(font.isReleased());
        assertThrows(IllegalStateException.class, () -> font.glyphIndex('a'));
    }

    @Test
    void openTypeCffIsUnsupported() {
        UiException failure = assertThrows(UiException.class, () -> UiFont.load(resource(UiFixtures.openTypeCff())));
        assertEquals(UiFont.STAGE, failure.stage());
        assertTrue(failure.reason().contains("not a TrueType font"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"empty", "short", "png", "truncated", "collection", "noTables", "missingTable"})
    void malformedFontsAreRejectedBeforeNativeUse(String kind) {
        byte[] font = UiFixtures.font(UiFixtures.FONT_A);
        byte[] bytes = switch (kind) {
            case "empty" -> new byte[0];
            case "short" -> new byte[] {0, 1, 0, 0};
            case "png" -> UiFixtures.png(2, 2, 0);
            case "truncated" -> UiFixtures.truncatedFont();
            case "collection" -> {
                byte[] copy = font.clone();
                copy[0] = 't';
                copy[1] = 't';
                copy[2] = 'c';
                copy[3] = 'f';
                yield copy;
            }
            case "noTables" -> {
                byte[] copy = font.clone();
                copy[4] = 0;
                copy[5] = 0;
                yield copy;
            }
            case "missingTable" -> {
                byte[] copy = font.clone();
                for (int i = 0; i < ((copy[4] & 0xFF) << 8 | copy[5] & 0xFF); i++) {
                    int record = 12 + i * 16;
                    if (copy[record] == 'g' && copy[record + 1] == 'l' && copy[record + 2] == 'y') {
                        copy[record] = 'x';
                    }
                }
                yield copy;
            }
            default -> throw new IllegalArgumentException(kind);
        };
        assertThrows(UiException.class, () -> UiFont.load(resource(bytes)));
    }
}
