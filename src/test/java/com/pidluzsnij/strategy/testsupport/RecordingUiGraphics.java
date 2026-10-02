package com.pidluzsnij.strategy.testsupport;

import com.pidluzsnij.strategy.ui.asset.GlyphAtlas;
import com.pidluzsnij.strategy.ui.asset.UiImage;
import com.pidluzsnij.strategy.ui.definition.TextAlign;
import com.pidluzsnij.strategy.ui.definition.UiColor;
import com.pidluzsnij.strategy.ui.layout.ScreenRect;
import com.pidluzsnij.strategy.ui.render.UiGraphics;
import com.pidluzsnij.strategy.ui.render.UiTextFace;
import com.pidluzsnij.strategy.ui.render.UiTexture;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Controlled {@link UiGraphics} boundary: records lifecycle events and every drawing request, tracks
 * allocations and releases, and can fail at any stage.
 */
public final class RecordingUiGraphics implements UiGraphics {

    /** A drawing request. */
    public sealed interface Draw permits ImageDraw, TextDraw {
    }

    public record ImageDraw(String texture, ScreenRect rect) implements Draw {
    }

    public record TextDraw(String text, String font, int bakedPixelHeight, float pixelSize, ScreenRect rect,
                           TextAlign align, UiColor color) implements Draw {
    }

    public final List<String> events;
    public final List<Draw> draws = Collections.synchronizedList(new ArrayList<>());
    public final List<Texture> textures = new ArrayList<>();
    public final List<Face> faces = new ArrayList<>();
    public final List<UiImage> images = new ArrayList<>();
    public int frames;
    /** Visible area of the most recent frame. */
    public ScreenRect visibleArea;
    public int closes;

    public RuntimeException initializeFailure;
    public RuntimeException closeFailure;
    /** Texture creation fails when this many textures already exist. */
    public int failTextureAt = -1;
    public RuntimeException textureFailure = new IllegalStateException("simulated texture creation failure");
    /** Text face creation fails when this many faces already exist. */
    public int failFaceAt = -1;
    /** Releasing the texture with this name fails (the texture still counts as released). */
    public String failTextureReleaseFor;

    public RecordingUiGraphics() {
        this(Collections.synchronizedList(new ArrayList<>()));
    }

    public RecordingUiGraphics(List<String> events) {
        this.events = events;
    }

    @Override
    public void initialize() {
        events.add("ui-initialize");
        if (initializeFailure != null) {
            throw initializeFailure;
        }
    }

    @Override
    public UiTexture createTexture(String name, UiImage image) {
        if (failTextureAt == textures.size()) {
            throw textureFailure;
        }
        events.add("ui-createTexture " + name);
        images.add(image);
        Texture texture = new Texture(name, image.width(), image.height());
        textures.add(texture);
        return texture;
    }

    @Override
    public UiTextFace createTextFace(GlyphAtlas atlas) {
        if (failFaceAt == faces.size()) {
            throw new IllegalStateException("simulated text face creation failure");
        }
        events.add("ui-createTextFace " + atlas.fontName() + " " + atlas.pixelHeight());
        Face face = new Face(atlas);
        faces.add(face);
        return face;
    }

    @Override
    public void beginFrame(int framebufferWidth, int framebufferHeight, ScreenRect visibleArea) {
        frames++;
        this.visibleArea = visibleArea;
    }

    @Override
    public void drawImage(UiTexture texture, ScreenRect rect) {
        Texture recorded = (Texture) texture;
        if (recorded.releases > 0) {
            throw new IllegalStateException("drawing a released texture");
        }
        draws.add(new ImageDraw(recorded.name(), rect));
    }

    @Override
    public void drawText(UiTextFace face, String text, ScreenRect rect, TextAlign align, UiColor color,
                         float pixelSize) {
        Face recorded = (Face) face;
        draws.add(new TextDraw(text, recorded.atlas().fontName(), recorded.atlas().pixelHeight(), pixelSize, rect,
                align, color));
    }

    @Override
    public void endFrame() {
    }

    @Override
    public void close() {
        closes++;
        events.add("ui-close");
        if (closeFailure != null) {
            throw closeFailure;
        }
    }

    /** @return whether every texture and face created so far was released exactly once */
    public boolean everythingReleasedOnce() {
        return textures.stream().allMatch(t -> t.releases == 1) && faces.stream().allMatch(f -> f.releases == 1);
    }

    /** @return whether no texture or face was released more than once */
    public boolean nothingReleasedTwice() {
        return textures.stream().allMatch(t -> t.releases <= 1) && faces.stream().allMatch(f -> f.releases <= 1);
    }

    public final class Texture implements UiTexture {
        private final String name;
        private final int width;
        private final int height;
        public int releases;

        Texture(String name, int width, int height) {
            this.name = name;
            this.width = width;
            this.height = height;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public int width() {
            return width;
        }

        @Override
        public int height() {
            return height;
        }

        @Override
        public void close() {
            releases++;
            events.add("ui-releaseTexture " + name);
            if (name.equals(failTextureReleaseFor)) {
                throw new IllegalStateException("simulated texture release failure");
            }
        }
    }

    public final class Face implements UiTextFace {
        private final GlyphAtlas atlas;
        public int releases;

        Face(GlyphAtlas atlas) {
            this.atlas = atlas;
        }

        @Override
        public GlyphAtlas atlas() {
            return atlas;
        }

        @Override
        public void close() {
            releases++;
            events.add("ui-releaseFace " + atlas.fontName());
        }
    }
}
