package com.pidluzsnij.strategy.ui;

import com.pidluzsnij.strategy.localization.Localization;
import com.pidluzsnij.strategy.ui.asset.GlyphAtlas;
import com.pidluzsnij.strategy.ui.asset.UiFont;
import com.pidluzsnij.strategy.ui.asset.UiImage;
import com.pidluzsnij.strategy.ui.asset.UiImageDecoder;
import com.pidluzsnij.strategy.ui.definition.TextStyle;
import com.pidluzsnij.strategy.ui.definition.UiComponent;
import com.pidluzsnij.strategy.ui.definition.UiDefinition;
import com.pidluzsnij.strategy.ui.definition.UiDefinitionParser;
import com.pidluzsnij.strategy.ui.input.PointerListener;
import com.pidluzsnij.strategy.ui.input.UiActivation;
import com.pidluzsnij.strategy.ui.layout.LayoutTransform;
import com.pidluzsnij.strategy.ui.render.UiGraphics;
import com.pidluzsnij.strategy.ui.render.UiTextFace;
import com.pidluzsnij.strategy.ui.render.UiTexture;
import com.pidluzsnij.strategy.ui.resource.ResolvedUiResource;
import com.pidluzsnij.strategy.ui.resource.UiResourcePath;
import com.pidluzsnij.strategy.ui.resource.UiResources;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The UI foundation: UI resource resolution, declarative definitions, image and font loading, rendering
 * through the application's OpenGL context and pointer interaction. It owns every UI graphics and native
 * resource it creates and releases them in {@link #close()}.
 * <p>
 * The foundation renders only a UI that application code explicitly {@linkplain #show shows}; with nothing
 * shown it submits nothing for rendering and ignores pointer input. Activations are reported to application
 * code as semantic {@link UiActivation}s; the foundation performs no application behavior itself.
 */
public final class UiFoundation implements AutoCloseable {

    static final String STAGE_LOCATION = "resolve external UI override location";
    static final String STAGE_RENDERING = "initialize UI rendering";
    static final String STAGE_TEXTURE = "create UI texture";
    static final String STAGE_RASTERIZE = "rasterize UI font glyphs";
    static final String STAGE_TEXT_FACE = "create UI text resources";

    private static final Logger log = LoggerFactory.getLogger(UiFoundation.class);

    private final UiResources resources;
    private final UiDefinitionParser parser;
    private final UiGraphics graphics;
    private final Localization localization;
    private final int framebufferWidth;
    private final int framebufferHeight;
    private final List<UiScreen> screens = new ArrayList<>();
    private final Map<UiResourcePath, UiScreen> required = new LinkedHashMap<>();
    private final PointerListener pointerListener = new ActiveScreenPointerListener();

    private boolean renderingInitialized;
    private UiScreen active;
    private Consumer<UiActivation> activationListener = activation -> { };
    private boolean closed;

    private UiFoundation(UiResources resources, UiDefinitionParser parser, UiGraphics graphics,
                         Localization localization, int framebufferWidth, int framebufferHeight) {
        this.resources = resources;
        this.parser = parser;
        this.graphics = graphics;
        this.localization = localization;
        this.framebufferWidth = framebufferWidth;
        this.framebufferHeight = framebufferHeight;
    }

    /** The stage in progress and its resource, for the failure diagnostic. */
    private static final class Progress {
        private String stage;
        private String resource;

        void enter(String stage, String resource) {
            this.stage = stage;
            this.resource = resource;
        }
    }

    /**
     * Initializes the UI foundation after OpenGL initialization: resolves the external override root and loads
     * the required definitions. The shared rendering resources, which need OpenGL 2.0, are created here when
     * definitions are required and otherwise when the first definition is loaded, so a startup that loads no UI
     * makes no additional demands on the OpenGL context. Success is logged at INFO; a failure is logged once at
     * ERROR, everything allocated so far is released, and the failure is rethrown.
     *
     * @param framebufferWidth  current framebuffer width, used to rasterize text at its displayed size
     * @param framebufferHeight current framebuffer height
     */
    public static UiFoundation initialize(UiStartupConfiguration configuration, UiGraphics graphics,
                                          Localization localization, int framebufferWidth, int framebufferHeight)
            throws UiException {
        Objects.requireNonNull(configuration, "configuration");
        Objects.requireNonNull(graphics, "graphics");
        Objects.requireNonNull(localization, "localization");
        Progress progress = new Progress();
        UiFoundation foundation = null;
        try {
            progress.enter(STAGE_LOCATION, null);
            Path base = configuration.location().configDirectory();
            if (base == null) {
                throw new UiException(STAGE_LOCATION, null, "no per-user configuration directory was reported");
            }
            UiResources resources = new UiResources(configuration.bundledResources(), UiResources.externalRoot(base));
            foundation = new UiFoundation(resources, new UiDefinitionParser(configuration.supportedBehaviors()),
                    graphics, localization, framebufferWidth, framebufferHeight);

            if (!configuration.requiredDefinitions().isEmpty()) {
                foundation.initializeRendering(progress);
            }
            for (UiResourcePath definition : configuration.requiredDefinitions()) {
                UiScreen screen = foundation.load(definition, progress);
                foundation.required.put(definition, screen);
            }
            log.info("UI foundation initialized: rendering {}, {} UI definition(s) loaded, external override root {} ({})",
                    foundation.renderingInitialized ? "ready" : "deferred until a UI definition is loaded",
                    foundation.required.size(), resources.externalRoot(),
                    Files.isDirectory(resources.externalRoot()) ? "present" : "absent");
            return foundation;
        } catch (Exception | LinkageError e) {
            UiException failure = e instanceof UiException ui ? ui
                    : new UiException(progress.stage, progress.resource, "unexpected failure", e);
            log.error("UI foundation initialization failed: could not {}{}: {}", failure.stage(),
                    failure.resource() == null ? "" : " (resource: " + failure.resource() + ")", failure.reason(),
                    failure);
            List<Throwable> cleanupFailures = new ArrayList<>();
            if (foundation != null) {
                foundation.releaseAll(cleanupFailures);
            } else {
                UiScreen.releaseOne(graphics::close, cleanupFailures);
            }
            for (Throwable cleanupFailure : cleanupFailures) {
                log.error("UI cleanup after failed initialization failed", cleanupFailure);
            }
            throw failure;
        }
    }

    /** @return the screen of a definition loaded as required during initialization */
    public Optional<UiScreen> requiredScreen(UiResourcePath definition) {
        return Optional.ofNullable(required.get(definition));
    }

    /** @return the number of definitions loaded during initialization */
    public int requiredScreenCount() {
        return required.size();
    }

    /**
     * Loads a definition and all resources it references. The returned screen is owned by the foundation.
     *
     * @throws UiException when the definition or any resource is unavailable or invalid; nothing of the
     *                     definition remains allocated in that case
     */
    public UiScreen load(UiResourcePath definition) throws UiException {
        Progress progress = new Progress();
        try {
            return load(definition, progress);
        } catch (RuntimeException | LinkageError e) {
            throw new UiException(progress.stage, progress.resource, "unexpected failure", e);
        }
    }

    /** Creates the shared rendering resources once, before the first graphics resource. */
    private void initializeRendering(Progress progress) throws UiException {
        if (renderingInitialized) {
            return;
        }
        progress.enter(STAGE_RENDERING, null);
        try {
            graphics.initialize();
        } catch (RuntimeException | LinkageError e) {
            throw new UiException(STAGE_RENDERING, null, "the OpenGL UI renderer could not be created", e);
        }
        renderingInitialized = true;
    }

    private UiScreen load(UiResourcePath path, Progress progress) throws UiException {
        ensureOpen();
        initializeRendering(progress);
        Map<UiResourcePath, UiTexture> textures = new LinkedHashMap<>();
        Map<UiResourcePath, UiFont> fonts = new LinkedHashMap<>();
        Map<UiScreen.FaceKey, UiTextFace> faces = new LinkedHashMap<>();
        try {
            ResolvedUiResource definitionResource = resolve(path, progress);
            progress.enter(UiDefinitionParser.STAGE, definitionResource.name());
            UiDefinition definition = parser.parse(definitionResource);

            for (UiResourcePath image : definition.imageResources()) {
                ResolvedUiResource resource = resolve(image, progress);
                progress.enter(UiImageDecoder.STAGE, resource.name());
                UiImage decoded = UiImageDecoder.decode(resource);
                progress.enter(STAGE_TEXTURE, resource.name());
                textures.put(image, graphics.createTexture(resource.name(), decoded));
            }

            float scale = LayoutTransform.fit(definition.logicalWidth(), definition.logicalHeight(),
                    framebufferWidth, framebufferHeight).scale();
            float bakeScale = scale > 0f ? scale : 1f;
            Map<String, String> texts = new LinkedHashMap<>();
            Map<String, UiScreen.FaceKey> textFaces = new LinkedHashMap<>();
            Map<UiScreen.FaceKey, Set<Integer>> codePoints = new LinkedHashMap<>();
            for (UiComponent component : definition.components()) {
                TextStyle style = switch (component) {
                    case UiComponent.Text text -> text.text();
                    case UiComponent.Button button -> button.label().orElse(null);
                    case UiComponent.Image image -> null;
                };
                if (style == null) {
                    continue;
                }
                String text = localization.text(style.localizationKey());
                UiScreen.FaceKey key = new UiScreen.FaceKey(style.font(),
                        Math.max(1, Math.round(style.size() * bakeScale)));
                texts.put(component.id(), text);
                textFaces.put(component.id(), key);
                Set<Integer> set = codePoints.computeIfAbsent(key, k -> new LinkedHashSet<>());
                text.codePoints().forEach(set::add);
            }

            Map<UiFont, Set<Integer>> used = new LinkedHashMap<>();
            for (UiScreen.FaceKey key : codePoints.keySet()) {
                if (!fonts.containsKey(key.font())) {
                    ResolvedUiResource resource = resolve(key.font(), progress);
                    progress.enter(UiFont.STAGE, resource.name());
                    fonts.put(key.font(), UiFont.load(resource));
                }
                UiFont font = fonts.get(key.font());
                used.computeIfAbsent(font, f -> new LinkedHashSet<>()).addAll(codePoints.get(key));
                progress.enter(STAGE_RASTERIZE, font.name());
                GlyphAtlas atlas = font.rasterize(key.pixelHeight(),
                        codePoints.get(key).stream().mapToInt(Integer::intValue).toArray());
                progress.enter(STAGE_TEXT_FACE, font.name());
                faces.put(key, graphics.createTextFace(atlas));
            }

            UiScreen screen = new UiScreen(definition, textures, fonts, faces, texts, textFaces);
            screens.add(screen);
            used.forEach(this::warnAboutMissingGlyphs);
            return screen;
        } catch (UiException | RuntimeException | LinkageError e) {
            List<Throwable> failures = new ArrayList<>();
            textures.values().forEach(t -> UiScreen.releaseOne(t::close, failures));
            faces.values().forEach(f -> UiScreen.releaseOne(f::close, failures));
            fonts.values().forEach(f -> UiScreen.releaseOne(f::close, failures));
            failures.forEach(e::addSuppressed);
            throw e;
        }
    }

    /**
     * Warns once per font of a loaded definition when the font maps no glyph for some characters of the
     * localized text drawn with it. Only counts are logged, never the text or the characters.
     */
    private void warnAboutMissingGlyphs(UiFont font, Set<Integer> codePoints) {
        long missing = codePoints.stream().filter(codePoint -> !font.hasGlyph(codePoint)).count();
        if (missing > 0) {
            log.warn("UI font {} has no glyph for {} of the {} distinct character(s) of its localized text "
                            + "(language '{}'); those characters are drawn with the font's missing-glyph shape",
                    font.name(), missing, codePoints.size(), localization.effectiveLanguage());
        }
    }

    private ResolvedUiResource resolve(UiResourcePath path, Progress progress) throws UiException {
        progress.enter("resolve UI resource", path.value());
        return resources.resolve(path);
    }

    /**
     * Shows {@code screen}: it is rendered every frame and receives pointer input; its activations are passed
     * to {@code listener}.
     */
    public void show(UiScreen screen, Consumer<UiActivation> listener) {
        ensureOpen();
        if (!screens.contains(screen)) {
            throw new IllegalArgumentException("the screen does not belong to this UI foundation");
        }
        active = screen;
        activationListener = Objects.requireNonNull(listener, "listener");
    }

    /** Stops showing any screen. */
    public void hide() {
        active = null;
        activationListener = activation -> { };
    }

    /** @return the screen being shown, if any */
    public Optional<UiScreen> activeScreen() {
        return Optional.ofNullable(active);
    }

    /** Draws the shown screen, if any, over the current frame. With nothing shown, nothing is submitted. */
    public void render(int framebufferWidth, int framebufferHeight) {
        if (active != null && !closed) {
            active.render(graphics, framebufferWidth, framebufferHeight);
        }
    }

    /** @return the listener the window system delivers pointer input to */
    public PointerListener pointerListener() {
        return pointerListener;
    }

    /**
     * Releases every UI screen and the shared rendering resources. Every release is attempted even when an
     * earlier one fails; later calls do nothing.
     *
     * @throws UiCleanupException when any release failed, carrying each failure as a suppressed exception
     */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        List<Throwable> failures = new ArrayList<>();
        releaseAll(failures);
        if (!failures.isEmpty()) {
            UiCleanupException exception = new UiCleanupException(failures.size() + " UI resource release(s) failed");
            failures.forEach(exception::addSuppressed);
            throw exception;
        }
        log.debug("UI foundation closed: UI graphics and native resources freed");
    }

    private void releaseAll(List<Throwable> failures) {
        closed = true;
        active = null;
        for (UiScreen screen : screens) {
            screen.release(failures);
        }
        UiScreen.releaseOne(graphics::close, failures);
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("the UI foundation has been closed");
        }
    }

    private final class ActiveScreenPointerListener implements PointerListener {

        @Override
        public void pointerMoved(double x, double y, int width, int height) {
            if (active != null) {
                active.interaction().pointerMoved(x, y, width, height);
            }
        }

        @Override
        public void primaryButton(boolean pressed, double x, double y, int width, int height) {
            if (active != null) {
                active.primaryButton(pressed, x, y, width, height).ifPresent(activationListener);
            }
        }

        @Override
        public void pointerLeft() {
            if (active != null) {
                active.interaction().pointerLeft();
            }
        }
    }
}
