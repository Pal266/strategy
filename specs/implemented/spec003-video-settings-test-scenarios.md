# video-settings Test Scenarios

## Automated Test Scenarios

-   Feature: Default fullscreen setting. Setup: Create application
    settings without a persisted fullscreen value. Action: Resolve the
    effective video settings. Expected: `fullscreen` is `true`.

-   Feature: Configured fullscreen enabled. Setup: Load configuration
    with `video.fullscreen` set to `true`. Action: Resolve the startup
    window settings. Expected: The application is configured to start in
    fullscreen mode.

-   Feature: Configured fullscreen disabled. Setup: Load configuration
    with `video.fullscreen` set to `false`. Action: Resolve the startup
    window settings. Expected: The application is configured to start in
    windowed mode.

-   Feature: Invalid fullscreen value normalization. Setup: Load
    syntactically valid TOML with `video.fullscreen` containing a value
    that cannot be converted to the required boolean setting. Action:
    Initialize application configuration. Expected: `fullscreen` uses
    its default value `true`, and the normalized configuration persists
    `video.fullscreen` as `true`.

-   Feature: Default resolution setting. Setup: Create application
    settings without a persisted resolution. Action: Resolve the
    effective video configuration. Expected: Resolution is represented
    as `width = auto` and `height = auto`.

-   Feature: Automatic monitor resolution. Setup: Configure resolution
    as `auto`/`auto` and supply a starting monitor video-mode resolution
    of 1920×1080. Action: Resolve the effective application resolution.
    Expected: The effective application resolution is 1920×1080.

-   Feature: Automatic resolution fallback. Setup: Configure resolution
    as `auto`/`auto` and make the starting monitor video-mode resolution
    unavailable. Action: Resolve the effective application resolution.
    Expected: The effective application resolution is 1280×720.

-   Feature: Explicit configured resolution. Setup: Configure resolution
    with `width = 1600` and `height = 900`. Action: Resolve the
    effective application resolution. Expected: The effective
    application resolution is 1600×900 without replacing it based on the
    starting monitor's current resolution.

-   Feature: Positive explicit dimensions are configuration-valid
    independently of monitor modes. Setup: Configure both resolution
    dimensions as positive integers and provide monitor information that
    does not advertise that exact resolution. Action: Validate the
    application settings. Expected: The configured resolution remains
    valid and is not defaulted solely because the monitor does not
    advertise that exact mode.

-   Feature: Mixed numeric width and automatic height. Setup: Configure
    resolution with a positive numeric width and `height = auto`.
    Action: Initialize application configuration, then resolve startup
    window settings with a detectable starting monitor resolution. Expected: The entire configured
    resolution is treated as invalid, both configured values are
    replaced by the default `auto`/`auto`, and the effective application
    resolution is resolved from the starting monitor.

-   Feature: Mixed automatic width and numeric height. Setup: Configure
    resolution with `width = auto` and a positive numeric height.
    Action: Initialize application configuration, then resolve startup
    window settings with a detectable starting monitor resolution. Expected: The entire configured
    resolution is treated as invalid, both configured values are
    replaced by the default `auto`/`auto`, and the effective application
    resolution is resolved from the starting monitor.

-   Feature: Non-positive configured resolution. Setup: Configure both
    dimensions numerically with at least one dimension equal to or less
    than zero. Action: Initialize application configuration with a
    detectable starting monitor resolution. Expected: The entire
    configured resolution is treated as invalid, it is replaced by
    `auto`/`auto`, and the effective application resolution is resolved
    from the starting monitor.

-   Feature: Incompatible resolution value type. Setup: Load
    syntactically valid TOML where at least one resolution value is
    neither `auto` nor a positive integer. Action: Initialize
    application configuration with a detectable starting monitor
    resolution. Expected: The entire resolution setting is treated as
    invalid, it is replaced by `auto`/`auto`, and the effective
    application resolution is resolved from the starting monitor.

-   Feature: Invalid resolution with unavailable monitor resolution.
    Setup: Configure an invalid resolution and make the starting
    monitor's video-mode resolution unavailable. Action: Initialize
    application configuration and resolve the effective application
    resolution. Expected: The entire resolution falls back to
    `auto`/`auto`, and the effective application resolution is 1280×720.

-   Feature: Missing video settings normalization. Setup: Load a valid
    existing configuration that does not contain the new video settings.
    Action: Initialize application configuration. Expected:
    `video.fullscreen` is added with `true`, `video.resolution.width`
    and `video.resolution.height` are added as `auto`, and the complete
    normalized configuration is persisted.

-   Feature: Invalid resolution normalization. Setup: Load a valid
    configuration containing an invalid resolution combination. Action:
    Initialize application configuration. Expected: The persisted
    normalized configuration contains both resolution values set to
    `auto` rather than retaining either invalid or partially valid
    values.

-   Feature: Valid video settings do not trigger normalization. Setup:
    Load a complete configuration containing a valid boolean fullscreen
    value and a valid `auto`/`auto` or positive-integer resolution.
    Action: Initialize application configuration. Expected: The
    effective video settings preserve the configured values and the
    configuration is not rewritten because of the video settings.

-   Feature: TOML representation of video settings. Setup: Create
    complete effective application settings with fullscreen disabled and
    an explicit 1600×900 resolution. Action: Persist the configuration.
    Expected: `application-settings.toml` contains a `video` section
    with `fullscreen = false` and a `video.resolution` representation
    containing numeric `width = 1600` and `height = 900`.

-   Feature: TOML representation of automatic resolution. Setup: Create
    complete effective application settings with the default automatic
    resolution. Action: Persist the configuration. Expected:
    `application-settings.toml` represents both `video.resolution.width`
    and `video.resolution.height` with the `auto` value.

-   Feature: Existing window behavior through default video settings.
    Setup: Use default application settings and supply a detectable
    starting monitor resolution. Action: Resolve the startup window
    settings. Expected: Fullscreen is enabled and the application
    resolution matches the starting monitor's current video-mode
    resolution, preserving the previous default behavior.

-   Feature: Existing resolution fallback through default video
    settings. Setup: Use default application settings and make the
    starting monitor resolution unavailable. Action: Resolve the startup
    window settings. Expected: Fullscreen is enabled and the application
    resolution is 1280×720.

-   Constraint: Resolution is defaulted as one logical setting. Setup:
    Supply a resolution where one dimension is valid and the other is
    invalid. Action: Normalize the application settings. Expected:
    Neither configured dimension is retained independently; the
    resulting configuration resolution is `auto`/`auto`.

-   Constraint: Automatic resolution is resolved outside TOML
    persistence. Setup: Persist and load settings containing
    `auto`/`auto`. Action: Inspect the persisted/loaded application
    settings before startup resolution is resolved. Expected:
    Persistence retains the automatic resolution representation and does
    not replace it with monitor dimensions; monitor dimensions are
    resolved when startup window settings are determined.

-   Constraint: Existing application title remains unchanged. Setup:
    Initialize the application with video settings enabled. Action:
    Resolve the startup window settings. Expected: The window title
    remains exactly `My strategy`; this specification changes only
    fullscreen and resolution configuration behavior.

-   Feature or Constraint: Video startup order and logging ownership.
    Setup: Use isolated logging/configuration paths and controlled initialization stages.
    Action: Start with valid video settings; separately fail logging and configuration initialization.
    Expected: Logging precedes configuration and video/window initialization; either early failure prevents video/window initialization; video handling does not recreate or truncate log.log.

-   Feature or Constraint: Video diagnostics by mode.
    Setup: Use test-selected default and development logging modes and valid fullscreen choices with automatic and explicit resolutions.
    Action: Resolve video settings and startup resolution in each mode.
    Expected: Development logs the two DEBUG events with fullscreen choice, selection, effective dimensions, and source; default excludes these events; normal startup selects default and no mode setting is added.

-   Feature or Constraint: Normalization log reuse.
    Setup: Use isolated valid TOML cases with missing fullscreen/resolution, invalid fullscreen, and each invalid-resolution representation.
    Action: Initialize configuration in both modes, then resolve video settings.
    Expected: Invalid fullscreen produces one WARN for video.fullscreen; invalid resolution produces one WARN for logical video.resolution with its reason; development includes missing-default DEBUG records; video handling does not duplicate configuration diagnostics.

-   Feature or Constraint: Automatic fallback warning scope.
    Setup: Configure automatic resolution with unavailable monitor information, then separately configure explicit 1600×900 with unavailable monitor information.
    Action: Resolve startup resolution in both logging modes.
    Expected: Automatic selection uses 1280×720 and produces the inherited WARN once; explicit selection keeps 1600×900 and produces no automatic-fallback WARN; development DEBUG identifies the correct source.

-   Feature or Constraint: Configured video settings in successful window log.
    Setup: Use isolated full-startup fixtures with fullscreen/windowed choices and explicit/automatic resolution; observe successful black-window presentation.
    Action: Inspect the successful window INFO event in both modes.
    Expected: The event contains the title and actual resolution/fullscreen state; requested values are not reported as actual when the platform reports different values.

-   Feature or Constraint: Platform rejection of valid explicit dimensions.
    Setup: Supply valid positive dimensions and simulate window creation rejection.
    Action: Run startup and inspect configuration and diagnostics.
    Expected: An ERROR identifies window creation failure; no alternate-resolution retry or normalization rewrite occurs; normal termination is not logged and inherited shutdown closes logging and releases ownership.

-   Feature or Constraint: File preservation and test isolation.
    Setup: Use a temporary strategy directory with configuration and active logging containing a marker; instrument production-path access.
    Action: Normalize missing/invalid video settings, save atomically, and separately simulate save failure.
    Expected: Configuration saves preserve the current log and ownership; failed save preserves existing configuration; all I/O uses isolated paths and no production file is accessed.

-   Feature or Constraint: Video diagnostic value protection.
    Setup: Use configurations with invalid values containing unique sensitive markers and unrelated test settings.
    Action: Exercise normalization and video diagnostics in development mode.
    Expected: Operational fullscreen choices and effective dimensions may appear, but raw invalid markers, TOML contents, unrelated values, and complete snapshots do not.

-   Feature or Constraint: Inherited responsibilities and dependencies.
    Setup: Inspect effective dependencies and boundaries after video-settings implementation.
    Action: Verify settings model dependencies and startup resolution handling.
    Expected: Defaults and validation remain independent of GLFW, TOML, paths, and OS queries; monitor resolution is resolved outside persistence; no new dependency or logging provider is required.
