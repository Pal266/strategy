# ui-foundation Test Scenarios

## Automated Test Scenarios

-   Feature or Constraint: Bundled UI resource access independent of
    working directory. Setup: Put a valid test UI resource set on an
    isolated runtime classpath; exercise both an exploded resource
    directory and a temporary archive, with no external override
    present. Action: Resolve and load the same required UI resources
    from different unrelated process working directories. Expected: The
    same bundled resources are loaded in all cases. Resource access does
    not require the source tree, the process working directory, or an
    external `ui` directory.

-   Feature or Constraint: Stable external UI override location. Setup:
    Supply an isolated application per-user directory containing
    `application-settings.toml`, `log.log`, and a `ui` subdirectory with
    a valid override; use unrelated working directories. Action: Resolve
    the overridden UI resource. Expected: The override is resolved from
    the `ui` subdirectory of the isolated application directory
    regardless of working directory. No second UI-root setting,
    environment variable, command-line root, or working-directory lookup
    is used.

-   Feature: Per-resource partial overrides. Setup: Provide bundled
    resources A, B, and C and an external `ui` directory containing only
    a valid replacement for B. Action: Resolve and load A, B, and C.
    Expected: A and C come from bundled resources; B comes from the
    external override. The external directory need not contain copies of
    A or C.

-   Feature or Constraint: Missing external override uses bundled
    resource. Setup: Provide a valid required bundled resource and an
    external `ui` root in which the corresponding relative file does not
    exist. Action: Resolve the resource. Expected: The bundled resource
    is selected and loads successfully without treating the absent
    override as an error.

-   Feature or Constraint: Invalid existing override is authoritative
    and fails. Setup: Provide a valid bundled image, font, and UI
    definition. In separate cases, place a corresponding external
    override that exists but is unreadable, malformed, unsupported, or
    invalid for the required type. Action: Resolve and load each
    resource. Expected: Each case reports UI-resource failure for the
    external resource. The valid bundled counterpart is not substituted
    and no partial result is exposed.

-   Feature: Missing required resource. Setup: Request a required test
    UI resource for which neither an external file nor a bundled
    resource exists. Action: Initialize the consuming test UI
    definition. Expected: Initialization fails with safe resource/stage
    context and no partially initialized UI is made available.

-   Feature or Constraint: UI resource path confinement. Setup: Create
    test UI definitions that separately reference an absolute path,
    parent traversal, and path forms that would escape either UI root;
    place readable sentinel files outside the roots. Action: Validate
    and load each definition. Expected: Every escaping path is rejected
    before the sentinel file is read. Valid relative paths confined to
    the UI roots remain usable.

-   Constraint: External and bundled resources are read-only inputs.
    Setup: Record bytes, metadata, and existence of isolated external UI
    files, bundled test resources, `application-settings.toml`, and
    `log.log`. Action: Perform successful UI loading, bundled fallback,
    invalid-override failure, and cleanup. Expected: UI loading creates,
    edits, repairs, copies, or deletes none of the UI inputs. It does
    not modify settings or log contents except for normal logging
    records produced through the existing logging system.

-   Feature: Declarative presentation and layout. Setup: Provide two
    valid test UI definitions with the same component identifiers and
    semantic behavior identifiers but different externally defined
    positions, dimensions, ordering, image references, font selections,
    font sizes, and visual-state resources. Action: Parse both
    definitions and inspect their validated in-memory representations
    and renderer inputs. Expected: The representations reflect their
    respective external presentation/layout values without Java source
    changes or definition-specific Java constants.

-   Feature or Constraint: Declarative definitions contain no executable
    behavior. Setup: Inspect the supported UI-definition schema/parser
    and provide fixtures attempting to specify scripts, expressions,
    arbitrary class names, arbitrary method invocation, native-library
    loading, or another unsupported executable field. Action: Validate
    the fixtures and inspect the production dependency/dispatch
    boundary. Expected: Executable constructs are unsupported and
    rejected. Definitions may contain only supported declarative data
    and application-defined semantic behavior identifiers; application
    code owns the meaning and execution of those identifiers.

-   Feature: Semantic behavior identifier validation. Setup: Provide one
    definition referencing a supported test semantic behavior identifier
    and separate definitions containing unknown or malformed
    identifiers. Action: Validate each definition. Expected: The
    supported identifier is represented as declarative semantic data for
    application dispatch. Unknown or malformed identifiers are rejected
    before rendering; no arbitrary method/class resolution occurs.

-   Feature: Definition validation is all-or-nothing. Setup: Create
    fixtures with a valid component followed by, separately, malformed
    syntax, an unsupported required property value, invalid resource
    reference, duplicate component identifier, and unsupported behavior
    identifier. Action: Load each definition. Expected: The complete
    definition is rejected in every invalid case; the earlier valid
    component is not exposed as a usable partial UI tree.

-   Feature: Image decoding through the common resource path. Setup:
    Provide equivalent valid test images as bundled resources and
    external overrides, plus malformed image bytes. Action: Load images
    through the UI resource model and prepare them for OpenGL texture
    use. Expected: Valid bundled and external images follow the same
    decode path and produce equivalent image metadata/pixel results.
    Malformed image data is rejected without bundled fallback when it
    came from an existing override.

-   Feature: TrueType font loading and Unicode glyph support. Setup:
    Supply a valid test TrueType font containing representative Latin,
    Czech/Hungarian accented, Cyrillic Ukrainian, and other characters
    required by test strings for the four default languages. Action:
    Load the font through the UI resource model and rasterize
    representative strings through the font-rendering foundation.
    Expected: Required glyphs are addressable/rasterized as Unicode text
    without ASCII-only conversion or replacement caused by the UI
    foundation.

-   Feature: External font selection and size. Setup: Provide two valid
    test UI definitions that reference different test font resources and
    font sizes for the same text component. Action: Validate the
    definitions and inspect the font/rendering requests. Expected: Each
    request uses the externally defined font resource and size; neither
    selection is hardcoded for that test screen in Java.

-   Feature or Constraint: Localization-key text boundary. Setup:
    Provide a test UI definition whose text component references a test
    localization key and initialize localization with a distinctive
    Unicode value for that key. Action: Resolve the component text and
    pass it to the rendering boundary. Expected: The definition
    stores/references the localization key, application localization
    supplies the displayed value, and the renderer receives that value
    unchanged. No translated production text is embedded in the UI
    definition or UI renderer.

-   Feature: Textured rectangle and text rendering commands. Setup: Use
    a controlled rendering boundary with a validated test definition
    containing textured rectangular elements and text. Action: Render
    one test frame. Expected: Rendering produces the expected ordered
    rectangle/texture and text drawing requests through the existing
    OpenGL-based UI renderer without creating another window or Java UI
    toolkit.

-   Feature: Deterministic component rendering order. Setup: Define
    overlapping test components in a known external order. Action:
    Produce rendering requests for one frame. Expected: Rendering
    requests preserve the externally defined component order so the
    later/defined layering result is deterministic.

-   Feature: Resolution-independent uniform layout transform. Setup:
    Define a logical UI with a known authored aspect ratio and component
    bounds. Exercise framebuffers with the same aspect ratio, a wider
    aspect ratio, and a taller aspect ratio. Action: Calculate rendering
    transforms and resulting component bounds. Expected: The logical UI
    scales uniformly and is centered. Same-aspect output fills the
    framebuffer; wider/taller output preserves authored proportions and
    leaves unused area on the appropriate axis rather than stretching
    the UI.

-   Feature: Hit testing shares the rendering transform. Setup: Use the
    same logical component at multiple framebuffer aspect ratios,
    including coordinates in centered unused areas. Action: Transform
    logical bounds for rendering and perform pointer hit tests at
    inside, edge, outside, and unused-area coordinates. Expected: Hit
    testing agrees with the rendered component bounds at every
    resolution. Coordinates outside the logical UI area do not hit a
    component.

-   Feature: Pointer normal and hover states. Setup: Create a validated
    interactive test component with distinct normal and hovered
    visual-state definitions. Action: Move the controlled pointer from
    outside to inside its rendered bounds without pressing the primary
    button, then move it outside again. Expected: State changes normal →
    hovered → normal and selects the corresponding externally defined
    visual state.

-   Feature: Pointer press, leave, re-enter, and activation. Setup:
    Create one interactive test component with normal, hovered, and
    pressed states and a supported test semantic behavior identifier.
    Action: Press inside; move outside while held; move back inside
    while held; release inside. Expected: State changes hovered →
    pressed → normal → pressed, then release produces exactly one
    semantic activation and returns to hovered. No application behavior
    is executed by the resource definition itself.

-   Feature: Release outside does not activate. Setup: Press the primary
    button inside an interactive test component. Action: Move outside
    and release. Expected: The component is not activated and ends in
    normal state while the pointer remains outside.

-   Feature: Press beginning outside does not activate. Setup: Position
    the pointer outside an interactive test component. Action: Press
    outside, move inside while held, and release inside. Expected: The
    component may reflect only the state rules applicable to a press
    that did not originate on it, but no semantic activation is produced
    from that press.

-   Feature: Pointer input outside logical UI area. Setup: Use a
    framebuffer with unused centered area caused by aspect-ratio
    preservation and place an interactive component near the logical UI
    boundary. Action: Move, press, and release the pointer within the
    unused framebuffer area. Expected: No component becomes hovered or
    pressed and no activation is produced.

-   Feature or Constraint: UI initialization startup order. Setup:
    Observe controlled logging, configuration, localization, window
    creation, OpenGL initialization, UI initialization, and event-loop
    entry. Action: Run successful startup. Expected: UI initialization
    occurs only after logging, configuration, localization, window
    creation, and OpenGL initialization succeed, and before normal
    event-loop rendering begins.

-   Feature: Fatal UI initialization failure. Setup: Use isolated
    startup paths and separately cause a required UI definition, image,
    font, and rendering-initialization failure after OpenGL
    initialization. Action: Attempt full startup and observe loop entry,
    diagnostics, cleanup, and process outcome. Expected: Each failure
    emits the required ERROR, prevents normal event-loop entry, attempts
    UI/native and existing application cleanup, reports startup failure,
    and does not emit normal-termination INFO.

-   Feature or Constraint: UI native/resource cleanup. Setup: Instrument
    UI image/font/rendering allocations and existing window cleanup.
    Exercise successful initialization/shutdown and failures after
    different subsets of UI resources have been allocated. Action: Close
    normally or trigger the selected initialization failure. Expected:
    Every allocated UI/native resource is released at most once; cleanup
    remains safe for unallocated resources; existing window/GLFW cleanup
    is attempted. Failure of one cleanup operation does not suppress
    later cleanup attempts.

-   Feature: UI diagnostics and logging modes. Setup: Exercise bundled
    resource resolution, external override resolution, successful
    initialization, invalid existing override, and missing required
    resource in default and development logging modes. Action: Inspect
    logs. Expected: Successful UI readiness is reported at INFO; fatal
    failures are reported at ERROR with safe stage/resource context.
    Development additionally distinguishes bundled versus external
    resolution at DEBUG. Logs contain no raw resource bytes, translated
    text dumps, per-frame records, per-glyph records, pointer-motion
    spam, or repeated hover-state records.

-   Feature or Constraint: Existing production graphical behavior.
    Setup: Start the production application with no bundled UI
    resources and no external overrides using existing supported
    video/localization settings. Action: Observe renderer inputs
    across multiple frames, UI rendering-resource creation and the
    production UI-definition activation state. Expected: No production
    UI definition is activated, no UI rendering resources are created
    and no UI element is submitted for visible rendering. Existing
    black-frame output remains the only visible application content.

-   Feature: Deferred UI rendering initialization. Setup: Initialize the
    UI foundation once without required definitions and once with a
    required test definition. Action: Observe rendering-resource
    creation, then load a definition into the first instance, and
    separately make rendering initialization fail on that first load.
    Expected: Without required definitions no rendering resources are
    created at startup; they are created once, before the first
    definition's graphics resources. With a required definition they
    are created during startup. A deferred initialization failure
    rejects the load with safe stage context and allocates nothing.

-   Feature: Missing font glyphs warning. Setup: Provide a test font
    and localized text containing characters the font does not map.
    Action: Start with a required test definition using that text.
    Expected: Startup succeeds; exactly one WARN names the font
    resource, the effective language and the number of missing
    characters, and contains neither the text nor the characters. Text
    fully covered by its font produces no warning.

-   Feature: Blocking and non-blocking components. Setup: Define a
    test button partly covered by a blocking image and partly by a
    non-blocking decoration, and a blocking image below it. Action:
    Hover, press and release over each region. Expected: The button is
    hovered and activated through the decoration and where it is
    uncovered or above the lower image; over the blocking image it
    stays normal and is not activated. Buttons cannot be marked
    non-blocking and malformed blocking values are rejected.

-   Constraint: Existing application behavior regression. Setup: Run
    relevant existing controlled startup, localization, video, window
    lifecycle, configuration, logging, and close-behavior tests with
    successful UI initialization. Action: Compare externally observable
    behavior with the pre-SPEC006 requirements. Expected: Window title
    localization, fullscreen/windowed choice, resolution handling,
    responsiveness, close behavior, configuration normalization,
    localization behavior, logging ownership, and shutdown behavior
    remain unchanged except for the specified UI initialization/cleanup
    stages and diagnostics.

-   Constraint: Dependency and toolkit boundary. Setup: Inspect Maven
    dependencies and production UI code after implementation. Action:
    Verify dependency versions and rendering/toolkit usage. Expected:
    LWJGL STB 3.4.3 and its matching platform native artifact are added;
    existing LWJGL remains 3.4.3. UI rendering uses the existing OpenGL
    context. JavaFX, Swing/AWT widgets, another widget toolkit, and
    unlisted external dependencies are absent.

-   Constraint: External-modifiability architecture. Setup: Inspect
    production dependencies between UI definitions/resources,
    rendering/layout code, localization, and application behavior
    dispatch. Action: Verify the effective architecture and exercise two
    different test resource sets against the same compiled application
    classes. Expected: Supported presentation/layout changes are
    obtained from UI resources rather than screen-specific Java visual
    constants. Both resource sets produce their respective validated
    presentation/layout without recompilation, while semantic behavior
    remains application-owned Java behavior.

-   Constraint: Test and production isolation. Setup: Instrument
    production configuration/logging paths, the production external `ui`
    directory, and bundled production resources while running automated
    malformed-resource, override, rendering, input, and startup
    fixtures. Action: Run the complete SPEC006 automated suite.
    Expected: Tests use isolated paths/resources; production external UI
    files and bundled resources are neither modified nor required to
    contain test-only UI definitions, text, images, fonts, or semantic
    behavior identifiers.

## Manual Test Scenarios

-   Feature or Constraint: Production black-screen regression. Setup:
    Start the application normally with valid SPEC006 implementation and
    no external UI override files. Action: Observe the application after
    startup, switch to another application and return, and close it
    using an existing supported close action. Expected: The application
    continues to display a uniformly black screen with no visible menu,
    controls, text, images, or other production UI; it remains
    responsive and closes normally.
