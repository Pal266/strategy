# video-settings

## Type

feature

## Title

video-settings

## Description

Introduce the first production video settings in the application
configuration. Fullscreen mode and application resolution become
configurable while preserving the existing fullscreen and
monitor-resolution behavior as their default configuration.

## Features

-   Add a `video` section to the application settings containing a
    `fullscreen` setting and a `resolution` setting.
-   Persist the fullscreen setting as `video.fullscreen` in
    `application-settings.toml`.
-   The fullscreen setting accepts a boolean value and defaults to
    `true`.
-   Use the effective fullscreen setting to determine whether the
    application window starts in fullscreen or windowed mode.
-   Persist resolution as two values, `width` and `height`, under
    `video.resolution` in `application-settings.toml`.
-   The default resolution setting has both `width` and `height` set to
    `auto`.
-   Treat resolution as one logical setting: a valid configured
    resolution is either `auto` for both width and height or a positive
    integer for both width and height.
-   When both resolution values are `auto`, use the current video-mode
    resolution of the application's starting monitor when that
    resolution can be determined.
-   When both resolution values are `auto` and the starting monitor's
    resolution cannot be determined, use 1280×720 as the effective
    application resolution.
-   When both resolution values are positive integers, use those
    configured dimensions as the effective application resolution.
-   Treat the entire resolution setting as invalid when its values form
    a mixed `auto`/numeric combination, have incompatible types, contain
    non-positive numeric dimensions, or otherwise do not form one of the
    valid resolution representations.
-   When the configured resolution is invalid, replace the entire
    resolution setting with its default `auto`/`auto` value and resolve
    the effective application resolution from that default.
-   Use the existing configuration normalization behavior to persist
    default video settings when they were missing or invalid and to
    leave an already complete valid configuration unchanged.
-   When a previously saved configuration does not contain these video
    settings, add their default values and persist the normalized
    complete configuration.
-   Replace the existing hardcoded fullscreen startup choice with the
    effective `video.fullscreen` setting.
-   Replace the existing unconditional monitor-resolution startup choice
    with the effective `video.resolution` setting. The existing
    monitor-resolution behavior and 1280×720 fallback continue to apply
    when resolution is configured as `auto`/`auto`.
-   Preserve the existing application window title and all previously
    specified window behavior not explicitly changed by this
    specification.

### Diagnostic logging and startup integration

- Apply effective video settings only after logging and configuration
  initialization succeed, preserving the startup order established by
  `application-window` and `configuration`.
- Use the existing logging infrastructure and active logging mode. This
  feature introduces no logging-mode setting or additional log file.
- Reuse configuration's normalization diagnostics: an invalid fullscreen
  value produces a WARN identifying `video.fullscreen`; an invalid resolution
  produces one WARN identifying the logical `video.resolution` setting and
  its invalidity reason. Missing defaults produce configuration's DEBUG
  diagnostics. Do not duplicate these events in video/window handling.
- Record these additional events at DEBUG level in development mode only:

  | Event | Required information |
  |---|---|
  | Effective video settings are selected for startup | Fullscreen choice; automatic or explicit resolution selection |
  | Startup resolution is resolved | Effective width and height; source: monitor video mode, automatic fallback, or explicit configuration |

- Preserve the window specification's INFO event for successful black-window
  presentation, including actual resolution and fullscreen state.
- Emit the existing WARN for unavailable monitor resolution only when
  automatic resolution selection requires the 1280×720 fallback. Explicit
  dimensions must not produce that fallback warning solely because monitor
  resolution information is unavailable.
- If window creation fails while applying valid video settings, use the
  existing ERROR and shutdown policy; do not silently retry with a different
  resolution or rewrite the valid configuration as invalid.

## Constraints

-   Resolution validity in the application-settings model is limited to
    configuration validity. Explicit positive numeric dimensions must
    not be rejected merely because the current monitor does not
    advertise that exact video mode.
-   The resolution setting must be validated and defaulted as a single
    logical setting; width and height must not independently fall back
    in a way that can produce a mixed effective `auto`/numeric
    resolution.
-   `auto` is a configuration value representing automatic resolution
    selection and must not be exposed to window creation as a numeric
    dimension.
-   Video setting defaults and validation belong to ApplicationSettings
    and must remain independent of TOML, NightConfig, filesystem paths,
    and GLFW monitor queries.
-   Monitor-resolution detection and the 1280×720 fallback remain
    platform/windowing concerns and must not become TOML persistence
    responsibilities.
-   This specification does not make the existing application window
    title configurable.

-   Diagnostic resolution dimensions and fullscreen state are permitted
    operational details. Do not dump raw TOML or unrelated configuration
    values; retain configuration's diagnostic value-protection policy.
-   Normal startup remains in default logging mode. Video diagnostics use
    the levels above without redefining the inherited logging policy.
-   Automated startup tests must isolate both configuration and logging
    paths and must not access production files.
-   Existing configuration normalization, atomic saving, logging ownership,
    and cleanup behavior remain unchanged except for the concrete video
    settings and events explicitly introduced here.

## Blockers

-   application-window
-   configuration
