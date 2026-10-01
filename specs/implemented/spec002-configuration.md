# configuration

## Type

infrastructure

## Title

configuration

## Description

Establish application configuration infrastructure that provides
application settings independently of their persistence format, persists
them as TOML in the operating system's conventional per-user
configuration location, and prepares persisted configuration for use
during application startup. This specification establishes the
infrastructure only; concrete production settings are introduced by
later specifications.

## Features

-   Provide a root application-settings model that can represent the
    application's configuration and can be extended with concrete
    settings by later specifications.
-   Provide persistence access to application settings without exposing
    TOML or NightConfig-specific types to consumers of the persistence
    abstraction.
-   Persist application settings in TOML 1.0 format.
-   Resolve the production configuration base directory using
    directories-jvm's per-user configuration directory for the current
    operating system.
-   Store production configuration in the `strategy` application
    directory with the filename `application-settings.toml`.
-   Allow configuration persistence to operate against an explicitly
    supplied location so automated tests can use isolated temporary
    directories instead of the user's production configuration location.
-   Distinguish a successfully loaded configuration, a configuration
    that does not exist, and a configuration that could not be loaded.
-   Treat a missing configuration as a normal first-run condition:
    create the effective default application settings, create any
    required configuration directories, persist the complete settings
    snapshot, and continue application startup.
-   Treat a configuration persistence failure during application startup
    as fatal. This includes failure to load an existing configuration,
    failure to create required configuration directories, and failure to
    persist configuration required during startup.
-   Treat syntactically malformed TOML as a persistence load failure
    rather than as a missing configuration or an individual invalid
    setting.
-   Apply application-owned defaults to recognized settings that are
    missing or invalid in successfully parsed persisted configuration.
-   Treat a recognized setting as invalid when its persisted value
    cannot be converted to the required type or does not satisfy that
    setting's validation rules.
-   Ignore unknown persisted settings while loading and exclude them
    from the effective application settings.
-   Normalize successfully loaded configuration by producing a complete
    effective application-settings snapshot containing recognized
    settings with valid effective values.
-   Persist the normalized complete snapshot during startup only when
    normalization changed the persisted configuration because a missing
    value received a default, an invalid value received a default, or an
    unknown value was discarded.
-   When a later application version introduces a recognized setting
    that is absent from an older configuration, apply that setting's
    default and persist it as part of the normalized complete snapshot.
-   Do not rewrite a successfully loaded configuration during startup
    when normalization makes no changes.
-   Persist complete effective application-settings snapshots rather
    than only values that differ from defaults.
-   Save configuration using atomic replacement semantics so a failed
    save does not leave an existing `application-settings.toml`
    partially written or corrupted.
-   Continue application startup with the complete effective application
    settings after configuration initialization succeeds.

### Startup integration and diagnostic logging

- Initialize configuration only after the logging infrastructure established
  by `application-window` is ready, and before GLFW or window initialization.
  Logging initialization failure prevents configuration initialization.
- Use the existing application logging infrastructure and its active mode;
  configuration initialization must not recreate, truncate, close, or take
  ownership of the log file itself.
- Record the following configuration events. Both modes include INFO, WARN,
  and ERROR events; only development mode includes DEBUG events:

  | Event | Level | Required information |
  |---|---|---|
  | Configuration initialization succeeds | INFO | Effective settings are ready for subsequent startup |
  | Missing configuration is successfully created with defaults | INFO | Configuration file path; first-run creation completed |
  | A recognized invalid value is replaced with its default | WARN | Setting identifier; incompatible type or failed validation |
  | Configuration initialization fails | ERROR | Failed operation; configuration path when available; exception details and stack trace when available |
  | Configuration location is resolved | DEBUG | Absolute configuration file path |
  | A missing recognized setting receives its default | DEBUG | Setting identifier; missing-value reason |
  | An unknown setting is discarded | DEBUG | Setting identifier |
  | Existing configuration is successfully loaded | DEBUG | Configuration file path |
  | Normalization changes the configuration | DEBUG | Counts of missing values defaulted, invalid values defaulted, and unknown settings discarded |
  | A complete configuration snapshot is successfully saved | DEBUG | Configuration file path; first-run creation or normalization reason |
  | Loaded configuration requires no rewrite | DEBUG | Configuration file path; normalization made no changes |

- Log successful creation or saving only after persistence succeeds, and
  initialization success only after all required startup persistence succeeds.
- On fatal configuration initialization failure, stop subsequent startup,
  log the failure once, then close logging and release log-file ownership
  through the application shutdown flow. Do not emit the normal-termination
  INFO event for this failed startup. If logging cannot write the diagnostic,
  use its stderr fallback and still attempt logging cleanup and ownership release.
- Preserve `log.log` and its active ownership during configuration loads and
  saves, including atomic replacement and failed saves. Logging initialization
  must preserve `application-settings.toml`.

## Constraints

-   This specification introduces configuration infrastructure only and
    must not introduce concrete production video, audio, gameplay,
    window, or other application settings.
-   Existing window title, fullscreen, resolution, and other behavior
    defined by previously implemented specifications must remain
    unchanged until a later specification explicitly makes those values
    configurable.
-   NightConfig understands TOML: TOML parsing and serialization
    concerns belong to the TOML persistence implementation.
-   Persistence understands storage: persistence concerns include
    locating, loading, and safely saving persisted configuration, but
    not defining application setting defaults or validation rules.
-   ApplicationSettings understands configuration values/defaults:
    configuration values, defaults, and setting-specific validation
    rules belong to the application-settings model and must not depend
    on TOML, NightConfig, filesystem paths, or OS-specific configuration
    locations.
-   Application understands startup policy: the application startup flow
    decides how persistence outcomes affect startup, including first-run
    creation and fatal handling of persistence failures.
-   NightConfig-specific types and exceptions must not be exposed by the
    format-independent persistence abstraction or the
    application-settings model.
-   The semantic persistence load outcomes must distinguish loaded, not
    found, and failed states; the specification does not require a
    particular Java representation for those outcomes.
-   Configuration logging must use SLF4J and the existing Logback provider;
    do not add another logging provider or redefine the existing logging policy.
-   Normal startup remains in `default` logging mode. This specification adds
    no production logging-mode setting or user-facing mode control.
-   Do not log raw configuration values, complete settings snapshots, or TOML
    contents. Failure diagnostics must not disclose configuration value contents;
    retain operation, setting identifier, and failure details without such values.
    Diagnostic wording is developer-only and requires no localization.
-   Production logging may already have created the shared `strategy` directory.
    Configuration persistence must still create it when used independently.
-   Automated tests exercising application startup must isolate both logging
    and configuration paths and must not access the user's production log file.
-   Automated persistence tests must use isolated temporary directories
    and must not read, create, modify, or delete the user's production
    configuration.
-   Saving must create the required `strategy` configuration directory
    when it does not already exist.
-   A failed atomic save must preserve an existing valid configuration
    file.

## Blockers

-   application-window

## External Dependencies

-   NightConfig TOML 3.9.0 (`com.electronwill.night-config:toml`): provide
    TOML 1.0 parsing and serialization for the TOML persistence implementation.
