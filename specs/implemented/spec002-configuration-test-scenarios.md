# configuration Test Scenarios

## Automated Test Scenarios

-   Feature: Root application-settings model and format-independent
    persistence. Setup: Create application settings and a persistence
    consumer using only application-owned configuration and persistence
    APIs. Action: Compile and exercise the configuration load/save
    boundary. Expected: Application settings can pass through the
    persistence boundary without consumers depending on TOML or
    NightConfig-specific types.

-   Feature: TOML persistence. Setup: Use an isolated temporary
    directory and create application settings containing representative
    test-only values supported by the test model. Action: Persist the
    settings and load them again. Expected: The settings are stored as
    valid TOML 1.0 and the loaded recognized values match the persisted
    values.

-   Feature: Production configuration path resolution. Setup: Resolve
    the per-user configuration directory through directories-jvm for the
    current test platform. Action: Resolve the production application
    configuration path. Expected: The path is the directories-jvm
    configuration directory followed by
    `strategy/application-settings.toml`.

-   Feature: Explicit persistence location for tests. Setup: Create an
    isolated temporary directory and configure persistence to use it.
    Action: Save and load application settings. Expected: Configuration
    I/O occurs only under the supplied temporary directory and does not
    access the production configuration path.

-   Feature: Successful load outcome. Setup: Place a valid readable
    configuration file at the isolated configuration path. Action: Load
    the configuration through the persistence abstraction. Expected: The
    load result represents a successful load and provides the loaded
    application settings.

-   Feature: Missing configuration outcome. Setup: Use an isolated
    temporary directory in which the configuration file does not exist.
    Action: Load the configuration through the persistence abstraction.
    Expected: The load result represents configuration not found rather
    than a persistence failure.

-   Feature: Failed load outcome for unreadable storage. Setup: Arrange
    an isolated configuration path for which loading fails because of a
    filesystem or I/O condition. Action: Load the configuration through
    the persistence abstraction. Expected: The load result represents a
    persistence failure and is not reported as configuration not found.

-   Feature: Malformed TOML handling. Setup: Write syntactically
    malformed TOML to the isolated `application-settings.toml`. Action:
    Load the configuration. Expected: The load result represents a
    persistence failure and does not produce effective application
    settings.

-   Feature: First-run configuration creation. Setup: Start
    configuration initialization with no configuration file at the
    isolated configuration location. Action: Initialize application
    configuration. Expected: Default application settings are produced,
    the required application configuration directory is created, a
    complete settings snapshot is persisted, and configuration
    initialization succeeds.

-   Feature: Fatal startup behavior after load failure. Setup: Make an
    existing configuration fail to load. Action: Run application
    configuration initialization during startup. Expected: Configuration
    initialization fails, application startup does not continue, and the
    existing configuration is not overwritten.

-   Feature: Fatal startup behavior after directory-creation failure.
    Setup: Use a configuration location where the required application
    configuration directory cannot be created. Action: Initialize
    configuration when persistence requires that directory. Expected:
    Configuration initialization fails and application startup does not
    continue.

-   Feature: Fatal startup behavior after required save failure. Setup:
    Arrange a first-run or normalization case that requires a save and
    make persistence unable to complete that save. Action: Initialize
    application configuration. Expected: Configuration initialization
    fails and application startup does not continue.

-   Feature: Missing recognized value normalization. Setup: Load a valid
    test configuration that omits a recognized test setting with a
    defined default. Action: Initialize application configuration.
    Expected: The effective settings contain the setting's default value
    and the normalized complete snapshot is persisted with that value
    present.

-   Feature: Invalid recognized value with incompatible type. Setup:
    Load syntactically valid TOML in which a recognized test setting
    cannot be converted to its required type. Action: Initialize
    application configuration. Expected: The effective settings use that
    setting's default value and the normalized complete snapshot is
    persisted with the default value.

-   Feature: Invalid recognized value violating validation rules. Setup:
    Load syntactically valid TOML in which a recognized test setting has
    the required type but violates its test validation rule. Action:
    Initialize application configuration. Expected: The effective
    settings use that setting's default value and the normalized
    complete snapshot is persisted with the default value.

-   Feature: Unknown setting handling. Setup: Load valid TOML containing
    recognized test settings and an unknown setting. Action: Initialize
    application configuration. Expected: The unknown setting is ignored,
    is absent from the effective settings, and is absent from the
    normalized persisted snapshot.

-   Feature: Complete normalized snapshot. Setup: Load a valid
    configuration containing a combination of valid recognized values,
    missing recognized values, invalid recognized values, and unknown
    values. Action: Initialize application configuration. Expected: The
    effective settings contain all recognized test settings with valid
    effective values, and the persisted normalized snapshot contains all
    recognized settings and no unknown settings.

-   Feature: Configuration evolution with a newly recognized setting.
    Setup: Load a valid configuration representing an older schema that
    lacks a recognized test setting introduced by the current model.
    Action: Initialize application configuration. Expected: The new
    setting receives its default value and is added to the persisted
    complete normalized snapshot.

-   Feature: No rewrite when normalization is unchanged. Setup: Place a
    valid, complete configuration containing only recognized test
    settings with valid values and record its contents and last-modified
    state. Action: Initialize application configuration. Expected: The
    effective settings are loaded successfully and the configuration
    file is not rewritten.

-   Feature: Complete snapshot saving. Setup: Create complete effective
    test application settings containing both default-valued and
    non-default-valued recognized settings. Action: Save the
    configuration. Expected: The persisted TOML contains every
    recognized test setting, including settings whose effective values
    equal their defaults.

-   Feature: Required directory creation on save. Setup: Use an isolated
    temporary base directory where the `strategy` application
    configuration directory does not exist. Action: Save application
    settings to the application configuration location. Expected: The
    required directory is created and the complete configuration file is
    saved successfully.

-   Feature: Atomic replacement. Setup: Place a valid existing
    configuration at the isolated target path and arrange for
    replacement with a different complete settings snapshot. Action:
    Save the replacement configuration successfully. Expected: The
    target file contains the complete replacement snapshot and no
    partial intermediate content is observable as the final
    configuration.

-   Constraint: Existing configuration preservation after failed atomic
    save. Setup: Place a valid existing configuration at the isolated
    target path and arrange for an atomic replacement attempt to fail
    before replacement completes. Action: Attempt to save a different
    settings snapshot. Expected: Persistence reports a failure and the
    original configuration remains intact and readable.

-   Constraint: Separation of configuration responsibilities. Setup:
    Inspect the dependencies of the application-settings model,
    persistence abstraction, TOML persistence implementation, and
    application startup configuration flow. Action: Verify their
    configuration responsibilities and exposed types. Expected:
    TOML/NightConfig concerns are confined to the TOML implementation;
    storage concerns are confined to persistence; defaults and
    validation belong to ApplicationSettings; startup outcome policy
    belongs to the application; NightConfig-specific types and
    exceptions are not exposed by the persistence abstraction or
    ApplicationSettings.

-   Constraint: Configuration infrastructure introduces no production
    settings. Setup: Inspect the application-settings model and existing
    application behavior after implementing this specification. Action:
    Start the application with configuration infrastructure enabled.
    Expected: No concrete production video, audio, gameplay, window, or
    other setting has been introduced by this specification, and
    existing window title, fullscreen, resolution, and other previously
    specified behavior remains unchanged.

-   Constraint: External dependency versions. Setup: Open the Maven
    project and its resolved dependency list. Action: Run Maven
    verification and inspect the effective dependencies. Expected:
    NightConfig TOML (`com.electronwill.night-config:toml`) resolves at
    version 3.9.0; directories-jvm and logging reuse the dependencies
    established by `application-window`, without another SLF4J provider.

-   Feature or Constraint: Startup order and early logging failure.
    Setup: Use isolated logging and configuration locations and observe initialization stages; separately arrange logging initialization failure.
    Action: Run application startup in both cases.
    Expected: Successful startup initializes logging, then configuration, then GLFW/window; failed logging prevents any configuration or window initialization.

-   Feature or Constraint: Default-mode configuration events.
    Setup: Initialize logging in default mode and use test-only settings; separately exercise first run, unchanged valid configuration, invalid type, invalid validation, and fatal persistence failure.
    Action: Initialize configuration for each case and inspect log.log.
    Expected: The INFO, WARN, and ERROR events in the main specification occur at their specified levels with required details; DEBUG events are absent; success events occur only after required persistence succeeds.

-   Feature or Constraint: Development-mode configuration events.
    Setup: Initialize logging in development mode; use test-only configurations covering successful load, missing values, invalid values, unknown settings, normalized saving, first-run saving, and no rewrite.
    Action: Initialize configuration for each case and inspect log.log.
    Expected: All applicable event-table records are present at their specified levels; identifiers, paths, reasons, and normalization counts match the case; no false save or initialization-success record appears after persistence failure.

-   Feature or Constraint: Fatal configuration failure cleanup.
    Setup: Initialize logging successfully in an isolated directory; separately arrange location-resolution, load, malformed-TOML, directory-creation, and required-save failures; use a separately supplied failing configuration location when necessary.
    Action: Run startup for each failure, inspect the log, and attempt log ownership from another process after termination.
    Expected: Subsequent window startup does not occur; the failed operation and available path and exception details are logged once; normal termination is not logged; logging closes and ownership releases.

-   Feature or Constraint: Logging fallback during fatal configuration failure.
    Setup: Initialize logging and then simulate a log-write failure alongside a fatal configuration failure.
    Action: Run configuration initialization and application shutdown handling.
    Expected: The failure is reported through stderr fallback; window startup is prevented; logging cleanup and ownership release are attempted despite the failed write.

-   Feature or Constraint: Shared-directory file preservation.
    Setup: Use a temporary strategy directory containing a valid application-settings.toml; initialize logging and record a unique current-session marker.
    Action: Inspect configuration after log initialization; then perform configuration loading, successful atomic saving, and a failed atomic saving while logging remains active.
    Expected: Log initialization preserves configuration bytes; configuration operations preserve existing log records and do not truncate or replace log.log; exclusive ownership remains active until application shutdown; failed save preserves valid configuration.

-   Feature or Constraint: Startup test isolation.
    Setup: Configure a full-startup test with isolated logging and configuration paths and instrument production-path access.
    Action: Exercise successful startup and fatal startup paths.
    Expected: All file I/O stays within supplied temporary locations; neither production configuration nor production log files are accessed.

-   Feature or Constraint: No logging-mode production setting.
    Setup: Use the production settings model and normal startup; exercise development-mode diagnostics through test-controlled logging selection.
    Action: Inspect model and startup configuration.
    Expected: No concrete logging-mode setting or user-facing mode control is introduced; normal startup remains default and configuration does not change the active logging mode.

-   Feature or Constraint: Configuration diagnostic value protection.
    Setup: Use test-only configuration values with unique sensitive markers, including values causing type, validation, and malformed-TOML failures.
    Action: Exercise configuration initialization in both modes and inspect all emitted messages and exception diagnostics.
    Expected: Logs retain required identifiers and failure context but contain no raw sensitive markers, whole settings snapshots, or TOML contents.

-   Feature or Constraint: Configuration directory creation independently of logging.
    Setup: Do not initialize application logging; use a temporary persistence base without a strategy directory.
    Action: Save through the configuration persistence boundary, then separately simulate directory creation failure.
    Expected: Persistence creates the required directory and saves successfully; failure reports a persistence failure without requiring logging to create the directory.
