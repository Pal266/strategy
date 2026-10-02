package com.pidluzsnij.strategy.ui.render;

import com.pidluzsnij.strategy.testsupport.UiFixtures;
import com.pidluzsnij.strategy.ui.asset.GlyphAtlas;
import com.pidluzsnij.strategy.ui.asset.UiFont;
import com.pidluzsnij.strategy.ui.asset.UiImageDecoder;
import com.pidluzsnij.strategy.ui.definition.TextAlign;
import com.pidluzsnij.strategy.ui.definition.UiColor;
import com.pidluzsnij.strategy.ui.layout.ScreenRect;
import com.pidluzsnij.strategy.ui.render.gl.GlUiGraphics;
import com.pidluzsnij.strategy.ui.resource.ResolvedUiResource;
import com.pidluzsnij.strategy.ui.resource.UiResourceOrigin;
import com.pidluzsnij.strategy.ui.resource.UiResourcePath;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.lwjgl.glfw.GLFW.GLFW_FALSE;
import static org.lwjgl.glfw.GLFW.GLFW_VISIBLE;
import static org.lwjgl.glfw.GLFW.glfwCreateWindow;
import static org.lwjgl.glfw.GLFW.glfwDefaultWindowHints;
import static org.lwjgl.glfw.GLFW.glfwDestroyWindow;
import static org.lwjgl.glfw.GLFW.glfwInit;
import static org.lwjgl.glfw.GLFW.glfwMakeContextCurrent;
import static org.lwjgl.glfw.GLFW.glfwSetErrorCallback;
import static org.lwjgl.glfw.GLFW.glfwTerminate;
import static org.lwjgl.glfw.GLFW.glfwWindowHint;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_NO_ERROR;
import static org.lwjgl.opengl.GL11.GL_RGBA;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glClearColor;
import static org.lwjgl.opengl.GL11.glGetError;
import static org.lwjgl.opengl.GL11.glReadPixels;
import static org.lwjgl.system.MemoryUtil.NULL;

/**
 * The OpenGL UI renderer drawing into a real, hidden GLFW window's default framebuffer. Skipped when no
 * display or OpenGL implementation is available.
 */
class GlUiGraphicsTest {

    private static final int SIZE = 128;
    private static long window = NULL;

    @BeforeAll
    static void createHiddenContext() {
        GLFWErrorCallback.createPrint(System.err).set();
        boolean initialized;
        try {
            initialized = glfwInit();
        } catch (Throwable e) {
            initialized = false;
        }
        assumeTrue(initialized, "GLFW cannot be initialized here (no display)");
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        window = glfwCreateWindow(SIZE, SIZE, "ui-render-test", NULL, NULL);
        assumeTrue(window != NULL, "no OpenGL window can be created here");
        glfwMakeContextCurrent(window);
        GL.createCapabilities();
    }

    @AfterAll
    static void destroyContext() {
        if (window != NULL) {
            GL.setCapabilities(null);
            glfwMakeContextCurrent(NULL);
            glfwDestroyWindow(window);
            window = NULL;
        }
        glfwTerminate();
        GLFWErrorCallback previous = glfwSetErrorCallback(null);
        if (previous != null) {
            previous.free();
        }
    }

    private static int[] readPixel(int x, int y) {
        ByteBuffer pixel = MemoryUtil.memAlloc(4);
        try {
            // OpenGL rows start at the bottom.
            glReadPixels(x, SIZE - 1 - y, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, pixel);
            return new int[] {pixel.get(0) & 0xFF, pixel.get(1) & 0xFF, pixel.get(2) & 0xFF};
        } finally {
            MemoryUtil.memFree(pixel);
        }
    }

    @Test
    void drawsTexturedRectanglesAndTextIntoTheCurrentContext() throws Exception {
        GlUiGraphics graphics = new GlUiGraphics();
        try (UiFont font = UiFont.load(new ResolvedUiResource(UiResourcePath.of("fonts/a.ttf"), UiResourceOrigin.BUNDLED,
                UiFixtures.font(UiFixtures.FONT_A)))) {
            graphics.initialize();
            UiTexture red = graphics.createTexture("red", UiImageDecoder.decode(new ResolvedUiResource(
                    UiResourcePath.of("images/red.png"), UiResourceOrigin.BUNDLED, UiFixtures.png(4, 4, 0xFF0000FF))));
            UiTexture blue = graphics.createTexture("blue", UiImageDecoder.decode(new ResolvedUiResource(
                    UiResourcePath.of("images/blue.png"), UiResourceOrigin.BUNDLED, UiFixtures.png(2, 2, 0x0000FFFF))));
            GlyphAtlas atlas = font.rasterize(32, "Ж".codePoints().toArray());
            UiTextFace face = graphics.createTextFace(atlas);

            glClearColor(0f, 0f, 0f, 1f);
            glClear(GL_COLOR_BUFFER_BIT);
            // Clip to a 120×120 visible area: columns and rows from 120 on lie outside it.
            graphics.beginFrame(SIZE, SIZE, new ScreenRect(0, 0, 120, 120));
            graphics.drawImage(red, new ScreenRect(0, 0, 64, 64));
            graphics.drawImage(blue, new ScreenRect(32, 32, 64, 64));
            graphics.drawImage(red, new ScreenRect(100, 0, 28, 28));
            graphics.drawText(face, "Ж", new ScreenRect(64, 88, 56, 32), TextAlign.CENTER, new UiColor(0, 255, 0, 255), 32f);
            graphics.endFrame();

            assertEquals(GL_NO_ERROR, glGetError());
            int[] redPixel = readPixel(10, 10);
            int[] bluePixel = readPixel(60, 60);
            int[] blackPixel = readPixel(124, 10);
            int[] clippedRed = readPixel(110, 10);
            assertTrue(redPixel[0] > 200 && redPixel[2] < 50, java.util.Arrays.toString(redPixel));
            assertTrue(bluePixel[2] > 200 && bluePixel[0] < 50, "later rectangle drawn on top");
            assertTrue(blackPixel[0] < 10 && blackPixel[1] < 10 && blackPixel[2] < 10,
                    "drawing outside the visible area is clipped");
            assertTrue(clippedRed[0] > 200, "the part inside the visible area is drawn");
            int green = 0;
            for (int x = 64; x < 120; x++) {
                for (int y = 90; y < 120; y++) {
                    if (readPixel(x, y)[1] > 128) {
                        green++;
                    }
                }
            }
            assertTrue(green > 20, "glyph pixels are drawn: " + green);

            red.close();
            red.close();
            blue.close();
            face.close();
            face.close();
        } finally {
            graphics.close();
            graphics.close();
        }
        assertEquals(GL_NO_ERROR, glGetError());
    }

    @Test
    void closingWithoutInitializationIsSafe() {
        new GlUiGraphics().close();
    }
}
