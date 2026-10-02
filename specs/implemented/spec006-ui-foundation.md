# ui-foundation

## Type

infrastructure

## Title

ui-foundation

## Description

Establish the reusable rendering, input, resource, and layout foundation
for future application user interfaces. UI appearance and layout can be
customized through external per-user resources without modifying or
recompiling Java code, while application behavior remains controlled by
application code. This specification introduces no visible menu or other
production UI and preserves the existing black application output.

## Features

### UI resource model and overrides

-   Provide application-owned bundled default UI resources under one
    `ui` resource root on the runtime classpath. Bundled resources are
    the complete default source for UI resources required by the
    application and must work when running the Maven project from
    IntelliJ and from packaged runtime classpath resources;
    bundled-resource access must not depend on the process working
    directory or a source-tree filesystem path. This specification
    requires no bundled UI resources: production loads no UI
    definition, so the bundled `ui` root may be empty or absent until
    a later specification adds production UI content.
-   Support external per-user UI resource overrides under a `ui`
    subdirectory of the same operating-system conventional per-user
    `strategy` configuration directory used for
    `application-settings.toml` and `log.log`. Resolve this location
    through the application's established per-user directory convention
    rather than from the process working directory.
-   Resolve each requested UI resource independently. For a requested
    relative resource path, use the corresponding external file when
    that file exists; otherwise use the corresponding bundled resource.
    An external `ui` directory is optional, and users may override any
    supported individual resource without copying unrelated bundled
    resources.
-   Treat an external file that exists at the requested override path as
    authoritative for that resource. If the file is unreadable,
    malformed, unsupported, or otherwise invalid for its required
    resource type, report UI-resource initialization failure; do not
    silently substitute the bundled version of that same resource.
-   If neither a usable external override nor the required bundled
    resource exists, report UI-resource initialization failure.
-   External overrides are read-only application inputs. UI resource
    loading must not create, modify, repair, copy, or delete files under
    the external `ui` directory.
-   UI resource paths supplied by UI definitions must be relative paths
    confined to the UI resource root. Reject absolute paths, traversal
    outside the root, and other paths that would access files outside
    the selected external or bundled UI resource root.
-   Permit automated tests to supply isolated bundled and external UI
    resource sets and isolated per-user directory locations without
    reading or modifying the user's production files.

### Externally defined presentation and layout

-   Provide an external declarative UI-definition format capable of
    describing the presentation and layout properties supported by this
    foundation without requiring changes to or recompilation of Java
    source code.
-   UI definitions must support stable application-defined component
    identifiers and component types, resource references, dimensions,
    positions, and ordering sufficient for future screens to define
    their visual structure externally.
-   UI definitions must support externally configurable visual state
    definitions for interactive components, including at least normal,
    hovered, and pressed states.
-   UI definitions must support references to image resources and font
    resources through the UI resource model rather than embedding
    filesystem-specific absolute paths.
-   UI definitions must support text properties that reference
    localization keys. A UI definition must not require translated
    user-visible text to be embedded directly in the definition.
-   Non-interactive components block pointer input to interactive
    components drawn below them by default. A UI definition may mark a
    non-interactive component as a non-blocking decoration that
    pointer input passes through. Interactive components always block.
-   UI definitions may reference application-defined semantic behavior
    identifiers for future interactive components, but UI resource files
    must not contain or execute Java, scripts, expressions, native code,
    or other executable behavior. The meaning and execution of behavior
    identifiers remain controlled by application code.
-   Malformed UI definitions, unsupported required component/property
    values, invalid resource references, duplicate component identifiers
    within the same definition, or references to unsupported behavior
    identifiers must be rejected rather than partially applied.
-   Loading a UI definition must produce a complete validated in-memory
    representation before it becomes available for rendering. A failed
    definition must not expose a partially parsed UI tree to application
    consumers.

### Image and font resources

-   Support decoding UI images through the specified LWJGL STB
    dependency for use as OpenGL textures.
-   Support TrueType font resources and glyph rasterization through the
    specified LWJGL STB dependency for UI text rendering.
-   Text rendering must support Unicode text returned by the existing
    localization framework, including the characters required by the
    four default project languages: English, Ukrainian, Czech, and
    Hungarian.
-   Font selection and font size for supported UI text must be
    externally configurable through UI resources rather than hardcoded
    for individual future screens.
-   When a loaded font has no glyph for some characters of the localized
    text drawn with it, record one WARN per font of the loaded
    definition with the font resource, the effective language and the
    number of affected characters, without the text or characters.
    Missing glyphs are not a failure.
-   Image and font resource decoding must use resource bytes obtained
    through the UI resource model so bundled and external resources
    follow the same validation and rendering path after resource
    resolution.
-   Native and OpenGL resources created for UI images, fonts, and
    rendering must be releasable during application cleanup and after
    failed initialization.

### Rendering and layout foundation

-   Provide a UI rendering foundation capable of drawing textured
    rectangular elements and text through the existing OpenGL
    application context.
-   The UI renderer requires OpenGL 2.0. Its OpenGL resources are
    created during UI-foundation initialization only when UI
    definitions are required at startup, and otherwise when the first
    UI definition is loaded, so a startup that loads no UI definition
    places no additional requirement on the OpenGL context.
-   Interpret UI layout in a resolution-independent logical coordinate
    space and transform it to the current application framebuffer while
    preserving the authored aspect ratio.
-   When the framebuffer aspect ratio differs from the authored UI
    aspect ratio, scale the UI uniformly to fit within the framebuffer
    and center the resulting logical UI area. Unused framebuffer area
    remains outside the logical UI area rather than stretching the UI
    non-uniformly.
-   Pointer hit testing considers the topmost component at the pointer
    position that is interactive or blocking; when that component is
    blocking but not interactive, no component is hit.
-   Pointer hit testing must use the same logical-coordinate
    transformation as rendering so an interactive component's visible
    bounds and interactive bounds remain aligned at every supported
    application resolution.
-   Rendering order must follow the externally defined component
    ordering so overlapping components have deterministic visual order.
-   Text rendering must preserve the Unicode text supplied by
    application/localization code and must not replace it with hardcoded
    translated strings.

### Pointer input and component interaction state

-   Provide application-owned pointer input sufficient for future UI
    components to receive pointer position and primary-button
    press/release state from the existing GLFW window.
-   Determine interactive component state from pointer input and
    externally defined component bounds, supporting normal, hovered, and
    pressed states.
-   A press that begins inside an interactive component may produce that
    component's application-defined activation only when the primary
    button is released while the pointer remains inside the same
    component.
-   Moving the pointer outside a component while the primary button is
    held removes its pressed visual state; moving back inside before
    release restores the pressed state for that same press.
-   Pointer input outside the logical UI area must not hover, press, or
    activate a UI component.
-   The foundation must expose semantic activation to application code
    without defining production actions or implementing main-menu
    behavior in this specification.

### Startup, diagnostics, and existing behavior

-   Initialize the UI foundation only after logging, configuration,
    localization, window creation, and OpenGL initialization have
    succeeded.
-   A fatal UI-foundation initialization failure must use the
    application's existing failed-startup cleanup behavior and terminate
    startup without entering the normal rendering/event loop.
-   Record UI-foundation initialization success at INFO with safe
    summary information sufficient to identify that the UI system is
    ready. Record fatal UI resource, definition, image, font, or
    rendering initialization failures at ERROR with safe resource/stage
    context and exception details when available. Record missing font
    glyphs at WARN as described above.
-   In development logging mode, record DEBUG diagnostics for UI
    resource resolution sufficient to distinguish bundled-resource use
    from external-override use without logging raw resource contents or
    translated text.
-   Do not emit per-frame, per-glyph, pointer-motion, hover-state, or
    repeated resource-lookup logging during normal operation.
-   Preserve the existing successful application output as a uniformly
    black screen with no visible menu, controls, text, images, game
    objects, or other production UI content. The foundation may be
    exercised by automated test fixtures, but this specification does
    not activate a production UI definition.
-   Preserve existing window title localization, video settings,
    fullscreen/windowed behavior, resolution handling, responsiveness,
    close behavior, configuration normalization, localization behavior,
    logging ownership, and shutdown behavior except for the additional
    UI initialization and cleanup stages explicitly introduced here.

## Constraints

-   Use the existing Java 21, Maven, LWJGL 3.4.3, GLFW, OpenGL, logging,
    configuration, and localization infrastructure.
-   UI presentation and layout supported by this foundation must be
    modifiable through UI resources without changing or recompiling Java
    source code. Application/game behavior must remain implemented and
    controlled by Java code.
-   The external UI override root is exactly the `ui` subdirectory of
    the application's established per-user `strategy` configuration
    directory. Do not introduce a second configurable UI-root setting,
    command-line override, environment-variable override, or
    working-directory-based resource root in this specification.
-   Partial overrides operate per requested resource. Absence of an
    external file means bundled fallback for that resource; presence of
    an invalid external file means failure for that resource and must
    not trigger bundled fallback.
-   Sharing the parent per-user application directory must not couple UI
    resource loading to the contents of `application-settings.toml` or
    `log.log`; UI loading must not modify either file.
-   Bundled defaults remain application-owned runtime resources.
    External overrides must not mutate, replace, or write back to
    bundled resources.
-   UI definitions are declarative data only. They must not provide a
    mechanism to load arbitrary classes, invoke arbitrary methods,
    execute scripts or expressions, load native libraries, or otherwise
    execute code supplied by UI resources.
-   Resource resolution must prevent absolute-path and path-traversal
    access outside the UI roots.
-   User-visible text introduced by future UI specifications must
    continue to use the existing localization framework. This
    infrastructure specification introduces no production user-visible
    text and no production localization keys.
-   The foundation must not introduce runtime language switching;
    existing localization remains selected at startup according to its
    current behavior.
-   UI rendering must use the application's existing OpenGL context and
    buffer-swap lifecycle rather than creating a second graphical window
    or a separate Java UI toolkit.
-   Do not introduce JavaFX, Swing, AWT UI widgets, or another widget
    toolkit.
-   Automated tests must isolate configuration, logging, external UI
    paths, and test UI resources from the user's production files and
    bundled production resources.
-   UI test fixtures may contain test-only text, images, fonts,
    definitions, and semantic behavior identifiers; such fixtures must
    not become production UI content.
-   Native/resource cleanup must remain safe after partial UI
    initialization failure and must preserve the existing rule that
    cleanup attempts continue even when an earlier cleanup step fails.

## Blockers

-   application-window
-   configuration
-   video-settings
-   localization-framework
-   window-title-localization

## External Dependencies

-   LWJGL STB 3.4.3 (`org.lwjgl:lwjgl-stb`): provide image decoding and
    TrueType font rasterization for UI resources.
-   LWJGL STB 3.4.3 native artifact (`org.lwjgl:lwjgl-stb` with the
    operating-system-appropriate native classifier): provide the native
    STB runtime matching the project's existing LWJGL native selection.
