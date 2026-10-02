package com.pidluzsnij.strategy.ui;

import com.pidluzsnij.strategy.ui.asset.UiFont;
import com.pidluzsnij.strategy.ui.definition.TextStyle;
import com.pidluzsnij.strategy.ui.definition.UiComponent;
import com.pidluzsnij.strategy.ui.definition.UiDefinition;
import com.pidluzsnij.strategy.ui.definition.VisualState;
import com.pidluzsnij.strategy.ui.input.UiActivation;
import com.pidluzsnij.strategy.ui.input.UiInteraction;
import com.pidluzsnij.strategy.ui.layout.LayoutTransform;
import com.pidluzsnij.strategy.ui.render.UiGraphics;
import com.pidluzsnij.strategy.ui.render.UiTextFace;
import com.pidluzsnij.strategy.ui.render.UiTexture;
import com.pidluzsnij.strategy.ui.resource.UiResourcePath;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A completely loaded UI: its validated definition, the graphics resources for its images and text, the
 * localized text of its components and its pointer interaction state. Instances exist only after every
 * resource of the definition loaded successfully.
 */
public final class UiScreen {

    /** Key of a text face: font resource and baked pixel height. */
    record FaceKey(UiResourcePath font, int pixelHeight) {
    }

    private final UiDefinition definition;
    private final Map<UiResourcePath, UiTexture> textures;
    private final Map<UiResourcePath, UiFont> fonts;
    private final Map<FaceKey, UiTextFace> faces;
    /** Component identifier to the text supplied by localization for its text or label. */
    private final Map<String, String> texts;
    /** Component identifier to the face key of its text or label. */
    private final Map<String, FaceKey> textFaces;
    private final UiInteraction interaction;
    private boolean released;

    UiScreen(UiDefinition definition, Map<UiResourcePath, UiTexture> textures, Map<UiResourcePath, UiFont> fonts,
             Map<FaceKey, UiTextFace> faces, Map<String, String> texts, Map<String, FaceKey> textFaces) {
        this.definition = definition;
        this.textures = Map.copyOf(textures);
        this.fonts = Map.copyOf(fonts);
        this.faces = Map.copyOf(faces);
        this.texts = Map.copyOf(texts);
        this.textFaces = Map.copyOf(textFaces);
        List<UiInteraction.Target> targets = new ArrayList<>();
        for (UiComponent component : definition.components()) {
            if (component instanceof UiComponent.Button button) {
                targets.add(new UiInteraction.Target(button.id(), button.bounds(), button.behavior()));
            } else if (component.blocking()) {
                targets.add(UiInteraction.Target.blocker(component.id(), component.bounds()));
            }
        }
        this.interaction = new UiInteraction(definition.logicalWidth(), definition.logicalHeight(), targets);
    }

    /** @return the validated definition */
    public UiDefinition definition() {
        return definition;
    }

    /** @return the text displayed by component {@code id}, as supplied by localization */
    public Optional<String> text(String id) {
        return Optional.ofNullable(texts.get(id));
    }

    /** @return the current visual state of interactive component {@code id} */
    public VisualState state(String id) {
        return interaction.state(id);
    }

    UiInteraction interaction() {
        return interaction;
    }

    /** Draws the components in definition order. */
    void render(UiGraphics graphics, int framebufferWidth, int framebufferHeight) {
        if (released) {
            throw new IllegalStateException("the screen has been released");
        }
        LayoutTransform transform = LayoutTransform.fit(definition.logicalWidth(), definition.logicalHeight(),
                framebufferWidth, framebufferHeight);
        if (transform.scale() <= 0f) {
            return;
        }
        graphics.beginFrame(framebufferWidth, framebufferHeight, transform.logicalArea());
        try {
            for (UiComponent component : definition.components()) {
                switch (component) {
                    case UiComponent.Image image ->
                            graphics.drawImage(textures.get(image.image()), transform.toFramebuffer(image.bounds()));
                    case UiComponent.Text text -> drawText(graphics, transform, text.id(), text.bounds(), text.text());
                    case UiComponent.Button button -> {
                        graphics.drawImage(textures.get(button.image(interaction.state(button.id()))),
                                transform.toFramebuffer(button.bounds()));
                        button.label().ifPresent(label ->
                                drawText(graphics, transform, button.id(), button.bounds(), label));
                    }
                }
            }
        } finally {
            graphics.endFrame();
        }
    }

    private void drawText(UiGraphics graphics, LayoutTransform transform, String id,
                          com.pidluzsnij.strategy.ui.definition.Bounds bounds, TextStyle style) {
        graphics.drawText(faces.get(textFaces.get(id)), texts.get(id), transform.toFramebuffer(bounds), style.align(),
                style.color(), style.size() * transform.scale());
    }

    /**
     * Releases every graphics and native resource of the screen, continuing after failures.
     *
     * @param failures receives each release failure
     */
    void release(List<Throwable> failures) {
        if (released) {
            return;
        }
        released = true;
        for (UiTexture texture : textures.values()) {
            releaseOne(texture::close, failures);
        }
        for (UiTextFace face : faces.values()) {
            releaseOne(face::close, failures);
        }
        for (UiFont font : fonts.values()) {
            releaseOne(font::close, failures);
        }
    }

    /** @return whether the screen's resources have been released */
    public boolean isReleased() {
        return released;
    }

    static void releaseOne(Runnable release, List<Throwable> failures) {
        try {
            release.run();
        } catch (RuntimeException | LinkageError e) {
            failures.add(e);
        }
    }

    Optional<UiActivation> primaryButton(boolean pressed, double x, double y, int width, int height) {
        return interaction.primaryButton(pressed, x, y, width, height);
    }
}
