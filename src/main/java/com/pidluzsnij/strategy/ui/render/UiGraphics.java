package com.pidluzsnij.strategy.ui.render;

import com.pidluzsnij.strategy.ui.asset.GlyphAtlas;
import com.pidluzsnij.strategy.ui.asset.UiImage;
import com.pidluzsnij.strategy.ui.definition.TextAlign;
import com.pidluzsnij.strategy.ui.definition.UiColor;
import com.pidluzsnij.strategy.ui.layout.ScreenRect;

/**
 * Drawing backend of the UI renderer. The production implementation draws through the application's existing
 * OpenGL context, between the window system's frame clear and buffer swap; it never creates a window.
 * Methods are called on the thread that owns that context.
 */
public interface UiGraphics {

    /** Rendering initialization: creates the shared rendering resources. */
    void initialize();

    /** Creates a texture from a decoded image. */
    UiTexture createTexture(String name, UiImage image);

    /** Creates the resources for drawing text from {@code atlas}. */
    UiTextFace createTextFace(GlyphAtlas atlas);

    /**
     * Starts drawing UI elements over the current frame. Drawing is clipped to {@code visibleArea}, the
     * logical UI area, so nothing is drawn in unused framebuffer area where pointer input is ignored.
     */
    void beginFrame(int framebufferWidth, int framebufferHeight, ScreenRect visibleArea);

    /** Draws {@code texture} stretched over {@code rect}. */
    void drawImage(UiTexture texture, ScreenRect rect);

    /**
     * Draws {@code text} exactly as supplied, on one line within {@code rect}: vertically centered and aligned
     * horizontally by {@code align}, at {@code pixelSize} pixels.
     */
    void drawText(UiTextFace face, String text, ScreenRect rect, TextAlign align, UiColor color, float pixelSize);

    /** Finishes drawing UI elements for the current frame. */
    void endFrame();

    /** Releases the shared rendering resources; safe to call when initialization did not happen or failed. */
    void close();
}
