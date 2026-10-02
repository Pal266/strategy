package com.pidluzsnij.strategy.ui.asset;

import com.pidluzsnij.strategy.testsupport.UiFixtures;
import com.pidluzsnij.strategy.ui.UiException;
import com.pidluzsnij.strategy.ui.resource.ResolvedUiResource;
import com.pidluzsnij.strategy.ui.resource.UiResourceOrigin;
import com.pidluzsnij.strategy.ui.resource.UiResourcePath;
import com.pidluzsnij.strategy.ui.resource.UiResources;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URLClassLoader;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** PNG decoding through LWJGL STB from resolved UI resources. */
class UiImageDecoderTest {

    @TempDir
    Path temp;

    private static int gradient(int x, int y) {
        return (x * 40) << 24 | (y * 60) << 16 | 0x80 << 8 | 0xFF;
    }

    @Test
    void bundledAndExternalImagesFollowTheSameDecodePath() throws Exception {
        byte[] png = UiFixtures.png(5, 3, UiImageDecoderTest::gradient);
        Path configBase = temp.resolve("config");
        try (URLClassLoader loader = UiFixtures.classLoader(UiFixtures.resources()
                .put("images/bundled.png", png).put("images/override.png", UiFixtures.png(1, 1, 0))
                .writeBundledDirectory(temp.resolve("classpath")))) {
            UiFixtures.resources().put("images/override.png", png).writeExternal(configBase);
            UiResources resources = new UiResources(loader, UiResources.externalRoot(configBase));

            ResolvedUiResource bundled = resources.resolve(UiResourcePath.of("images/bundled.png"));
            ResolvedUiResource external = resources.resolve(UiResourcePath.of("images/override.png"));
            assertEquals(UiResourceOrigin.BUNDLED, bundled.origin());
            assertEquals(UiResourceOrigin.EXTERNAL, external.origin());

            UiImage fromBundled = UiImageDecoder.decode(bundled);
            UiImage fromExternal = UiImageDecoder.decode(external);

            assertEquals(5, fromBundled.width());
            assertEquals(3, fromBundled.height());
            assertEquals(fromBundled.width(), fromExternal.width());
            assertEquals(fromBundled.height(), fromExternal.height());
            assertArrayEquals(fromBundled.rgba(), fromExternal.rgba());
            for (int y = 0; y < 3; y++) {
                for (int x = 0; x < 5; x++) {
                    assertEquals(gradient(x, y), fromBundled.pixel(x, y), "pixel " + x + "," + y);
                }
            }
        }
    }

    private static ResolvedUiResource external(byte[] bytes) {
        return new ResolvedUiResource(UiResourcePath.of("images/x.png"), UiResourceOrigin.EXTERNAL, bytes);
    }

    @Test
    void malformedPngIsRejected() {
        UiException failure = assertThrows(UiException.class, () -> UiImageDecoder.decode(external(UiFixtures.malformedPng())));
        assertEquals(UiImageDecoder.STAGE, failure.stage());
        assertEquals("external images/x.png", failure.resource());
    }

    @Test
    void unsupportedImageFormatIsRejected() {
        UiException failure = assertThrows(UiException.class, () -> UiImageDecoder.decode(external(UiFixtures.gif())));
        assertTrue(failure.reason().contains("not a PNG"));
    }

    @Test
    void emptyAndTruncatedDataIsRejected() {
        assertThrows(UiException.class, () -> UiImageDecoder.decode(external(new byte[0])));
        byte[] png = UiFixtures.png(4, 4, 0xFFFFFFFF);
        byte[] truncated = java.util.Arrays.copyOf(png, png.length - 20);
        assertThrows(UiException.class, () -> UiImageDecoder.decode(external(truncated)));
    }

    @Test
    void oversizedImagesAreRejectedBeforeDecoding() {
        byte[] png = UiFixtures.png(1, 1, 0);
        // Patch the IHDR width to exceed the limit; the CRC mismatch is irrelevant to the size check.
        png[16] = 0;
        png[17] = 0;
        png[18] = (byte) 0x40;
        png[19] = 0;
        assertThrows(UiException.class, () -> UiImageDecoder.decode(external(png)));
    }
}
