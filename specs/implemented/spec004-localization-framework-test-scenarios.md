# localization-framework Test Scenarios

## Automated Test Scenarios

- Feature or Constraint: Initial metadata and infrastructure-only scope.
  Setup: Inspect the application's bundled localization resources and
  production configuration model after implementation.
  Action: Load the metadata and inspect all four initial localization files.
  Expected: The metadata contains en/English/english.properties,
  uk/Українська/ukrainian.properties, cs/Čeština/czech.properties, and
  hu/Magyar/hungarian.properties in that order. All four localization
  files are empty UTF-8 files. The only new production setting is
  localization.language; no production translation entries, translated
  application text, or language-selection UI is introduced.

- Feature: Resource lookup independent of working directory.
  Setup: Put localization resources on an isolated runtime classpath;
  exercise both an exploded resource directory and a temporary archive
  containing those resources.
  Action: Initialize localization from different unrelated working
  directories using the same runtime resources.
  Expected: The same metadata and selected file are loaded in all cases.
  Resource access does not require the source tree or a working-directory
  localization folder.

- Feature: Valid CSV parsing and exposed language information.
  Setup: Use valid metadata fixtures with LF and CRLF, empty physical
  lines, non-ASCII display names, a quoted display name containing a
  comma, and a quoted display name containing a doubled quote.
  Action: Initialize metadata and obtain available language information.
  Expected: Fields are decoded correctly as UTF-8; language identifiers
  and display names are exposed through application-owned APIs in
  metadata order. Quoted commas and doubled quotes are decoded correctly.

- Feature: Metadata-driven additional language.
  Setup: Include all four required entries and a fifth valid unique
  identifier/file mapping with a test-only localization file. Configure
  that fifth identifier.
  Action: Initialize localization and request a test key.
  Expected: The fifth file supplies the value without changes to a
  hardcoded language-to-file mapping; no English fallback or configuration
  rewrite occurs.

- Feature: Fatal metadata availability failures.
  Setup: Separately make languages.csv missing, unreadable, and invalid
  UTF-8 using isolated resource fixtures.
  Action: Attempt localization initialization.
  Expected: Each case fails at metadata loading, produces ERROR with safe
  failure context, and does not proceed to file selection or window startup.

- Feature: Fatal malformed metadata.
  Setup: Use separate fixtures for wrong header names/order, too few or
  too many fields, unmatched quotes, characters after a closing quote,
  a multiline quoted field, empty fields, leading/trailing field
  whitespace, duplicate language identifiers, duplicate localization
  filenames, and each missing required initial language entry.
  Action: Attempt localization initialization for every fixture.
  Expected: Every malformed metadata result is rejected in full;
  no partial language list is used, no successful initialization is logged,
  and window startup is prevented.

- Feature: Localization filename validation.
  Setup: Use metadata fixtures containing an absolute path, forward or
  backward directory separator, a traversal path, an empty filename, or
  a filename without the .properties suffix.
  Action: Attempt metadata loading.
  Expected: Invalid names make metadata malformed; the loader does not
  access resources outside the localization folder.

- Feature: Default language and older-configuration normalization.
  Setup: Use isolated configuration/logging paths and valid localization
  resources. Separately use no configuration file and a valid older
  configuration without localization.language.
  Action: Run startup configuration and localization initialization.
  Expected: ApplicationSettings contains language en; the complete
  existing TOML configuration is created or normalized with
  [localization] language = "en"; localization selects English.
  Existing configuration first-run/normalization rules remain applicable.

- Feature: Invalid language configuration.
  Setup: Use syntactically valid TOML cases with language set to a boolean,
  integer, floating-point number, array, table, empty string, whitespace-only
  string, or string with leading/trailing whitespace. Supply valid resources.
  Action: Initialize configuration and localization.
  Expected: Each invalid value defaults to en and is saved through existing
  complete-snapshot normalization. One configuration WARN identifies
  localization.language and the safe invalidity reason. Localization does
  not duplicate that normalization WARN or expose the invalid value.

- Feature: Strict identifier matching and unknown language fallback.
  Setup: Use valid metadata and a readable English file. Configure an
  unknown nonblank string, then separately configure EN while metadata
  contains only lowercase en for English.
  Action: Initialize localization.
  Expected: Exact matching treats both identifiers as unavailable;
  English becomes effective and one fallback WARN is emitted per run.
  The configured string remains unchanged and no fallback-only TOML
  rewrite occurs. Diagnostics do not echo the raw unknown identifier.

- Feature: Missing and wrong-shaped localization sections.
  Setup: Separately load valid TOML with no localization section and with
  localization represented as a scalar or array instead of a table.
  Action: Initialize configuration with valid localization resources.
  Expected: An absent section defaults language to en with configuration's
  missing-setting DEBUG. A wrong-shaped section is replaced with a table
  containing language = "en" and produces one configuration WARN identifying
  localization.language. Unrelated recognized settings remain unchanged.
  Required normalized snapshots are saved before localization starts.

- Feature or Constraint: Required configuration save and load failures.
  Setup: Use an older or invalid isolated configuration requiring language
  normalization and make its atomic save fail; separately use malformed
  TOML and an unreadable existing configuration.
  Action: Attempt startup.
  Expected: Each configuration failure prevents localization initialization.
  Existing valid configuration remains intact after failed saving. The
  existing configuration ERROR and cleanup policy apply; there is no
  localization-success event or window startup.

- Feature: Valid preference persistence and unrelated settings.
  Setup: Use complete valid isolated TOML containing localization.language
  = "uk" and representative existing recognized settings, with unchanged
  file bytes and last-modified state recorded.
  Action: Initialize configuration/localization; separately save a complete
  settings snapshot and reload it.
  Expected: Ukrainian is selected; startup makes no configuration rewrite.
  Save/reload retains localization.language and all other recognized
  settings. Default-valued language is also included in complete snapshots.

- Feature: Requested language selection rather than OS locale.
  Setup: Provide distinct test values in the four language files and vary
  simulated OS locale independently of configured language.
  Action: Initialize once for each configured en, uk, cs, and hu and look up
  the same test key.
  Expected: Each run uses the corresponding configured language's file and
  returns its value regardless of OS locale; configured and effective
  identifiers agree and no fallback WARN occurs.

- Feature: Empty selected file remains valid.
  Setup: Select a non-English language whose file is empty. Give English
  a usable value for a test key; separately run with the English file
  unavailable.
  Action: Initialize localization and look up that key.
  Expected: Initialization succeeds in the selected non-English language
  in both runs; lookup returns the key itself. English is not loaded,
  no file fallback is selected, and no availability WARN occurs.

- Feature: Unused file availability does not block initialization.
  Setup: Select a valid non-English file; separately make English and each
  other nonselected language file missing, unreadable, or malformed while
  keeping metadata valid.
  Action: Initialize localization and request a present test key.
  Expected: Selected-language initialization and lookup succeed.
  Nonselected files are not loaded or validated; their availability does
  not produce fallback or fatal diagnostics.

- Feature: Non-English file fallback.
  Setup: Use valid metadata, configure uk, and make its file missing,
  unreadable, invalid UTF-8, and malformed in separate runs. Supply a
  readable, valid English file with a test entry.
  Action: Initialize localization and look up the test key.
  Expected: Each unavailable Ukrainian file triggers one fallback WARN.
  English is effective and supplies the value; initialization succeeds.
  The saved Ukrainian preference and unrelated configuration remain unchanged.

- Feature: Empty English fallback.
  Setup: Configure a non-English language with an unavailable file and
  provide an empty but valid English file.
  Action: Initialize localization and request a valid test key.
  Expected: English fallback succeeds, INFO reports effective en and
  fallback use, and lookup returns the requested key. Empty English is
  not considered unavailable.

- Feature: Unavailable configured English.
  Setup: Configure en; separately make the English file missing, unreadable,
  invalid UTF-8, and malformed.
  Action: Attempt startup.
  Expected: Localization fails with ERROR, does not recursively retry en,
  and emits no redundant fallback WARN or initialization-success INFO.
  Window startup is prevented and fatal cleanup policy applies.

- Feature: Unavailable required English fallback.
  Setup: Configure a non-English language whose file is unavailable;
  separately make the English fallback missing, unreadable, invalid UTF-8,
  and malformed. Include an unknown configured identifier as another case.
  Action: Attempt startup.
  Expected: One fallback WARN is followed by one localization-failure ERROR.
  No further language is tried; window startup is prevented, configuration
  preference is preserved, and fatal cleanup policy applies.

- Feature: Translation format and exact value preservation.
  Setup: Use selected-language test fixtures with LF and CRLF, blank and
  whitespace-only lines, indented # comments, whitespace around keys,
  non-ASCII keys/values, a value containing additional = characters,
  a literal # inside a value, literal backslashes, and leading/trailing
  whitespace around a nonblank value.
  Action: Load the file and request its normalized keys.
  Expected: Ignored lines create no entries. The first = separates the
  trimmed key from the unmodified value. Non-ASCII content, extra =,
  inline #, backslashes, and value whitespace are preserved. No escape,
  interpolation, or continuation processing occurs.

- Feature: Malformed localization file rejects partial data.
  Setup: Place a valid test entry before an invalid record; separately use
  a record without =, an empty or whitespace-only key, duplicate exact
  keys, and keys that become duplicates after trimming.
  Action: Initialize with this selected non-English file and valid English
  fallback; separately exercise the same cases as selected English.
  Expected: The entire malformed file is unavailable; earlier entries or
  last duplicate values are not retained. Non-English cases use file
  fallback; English cases fail initialization.

- Feature: Present key lookup and case sensitivity.
  Setup: Load a selected-language file containing distinct test keys
  test.title and Test.Title.
  Action: Request each exact key, a missing case variant, and a valid
  whitespace-padded requested key.
  Expected: Exact keys return their distinct values. Missing case variants
  and whitespace-padded requested keys return their original requested
  keys unchanged; lookup does not trim caller input.

- Feature: Missing key without individual English fallback.
  Setup: Load a valid selected non-English file that lacks test.missing;
  put a usable test.missing translation in English.
  Action: Request test.missing.
  Expected: The result is exactly test.missing. English is not loaded for
  the lookup, the effective language does not change, and no per-key
  fallback occurs.

- Feature: Empty and whitespace-only values without individual fallback.
  Setup: Put test.empty= and whitespace-only values in the selected
  non-English file, including spaces, tabs, and Unicode White_Space
  characters. Put usable values for those keys in English. Include a
  nonblank value with surrounding whitespace.
  Action: Request all these keys.
  Expected: Empty and whitespace-only values return their requested keys
  unchanged with no English fallback. The nonblank value is returned
  exactly with its surrounding whitespace.

- Feature: Missing key or value in effective English.
  Setup: Select English directly, then separately select English through
  file fallback. Its file has a missing key, an empty value, and a
  whitespace-only value.
  Action: Request each corresponding valid key.
  Expected: All results are the requested keys unchanged; no recursive
  fallback, fatal failure, or language change occurs.

- Feature: Invalid lookup arguments.
  Setup: Initialize a valid localization framework.
  Action: Request null, an empty key, and whitespace-only keys.
  Expected: Each request is rejected as a programming error rather than
  returning a translation, selecting fallback, or invoking application
  shutdown. Valid later requests still work.

- Feature: In-memory lookup and next-startup changes.
  Setup: Load a test language, then change its fixture contents, metadata
  mapping, and configured language after initialization; instrument resource
  reads and configuration writes.
  Action: Repeat lookups in the existing instance, then initialize a fresh
  instance with the changed fixtures.
  Expected: Existing lookups retain the loaded selection and values and
  perform no resource reads or configuration writes. Fresh initialization
  observes the changed resources and configured selection.

- Feature or Constraint: Startup order and earlier failures.
  Setup: Observe controlled logging, configuration, localization, and
  GLFW/window stages using isolated paths and resources.
  Action: Run successful startup, then separately fail logging and
  configuration initialization.
  Expected: Successful order is logging, configuration, localization,
  then GLFW/window. Either earlier failure prevents localization;
  localization must succeed before window initialization.

- Feature or Constraint: Fatal localization shutdown.
  Setup: Initialize logging/configuration in isolated paths; arrange fatal
  metadata failure and fatal required-English failure in separate runs.
  Observe cleanup, process failure outcome, and logging ownership.
  Action: Attempt full startup, then attempt log ownership from another
  process after termination.
  Expected: No GLFW/window stage starts; localization failure is logged
  once; application cleanup is attempted; logging closes and ownership
  releases; the process reports failure. No normal-termination INFO is
  recorded, and the next process can acquire the log.

- Feature or Constraint: Logging and cleanup failure resilience.
  Setup: Initialize logging/configuration; combine fatal localization
  failure with an injected log-write failure, then separately inject an
  application cleanup failure.
  Action: Execute startup failure handling.
  Expected: Log-write failure uses stderr fallback, and cleanup and
  ownership-release attempts still occur. A failed cleanup operation
  does not suppress subsequent cleanup attempts. Normal termination is
  not logged.

- Feature: Localization diagnostics in both logging modes.
  Setup: Exercise direct English success, direct non-English success,
  unknown-identifier fallback, file fallback success, invalid metadata,
  direct English failure, and failed English fallback in default and
  development modes.
  Action: Inspect recorded events.
  Expected: Applicable INFO/WARN/ERROR records contain the required safe
  context. Development additionally records successful metadata/file
  loads with accurate counts; default excludes DEBUG. Initialization
  success is absent after failure. Fallback WARN occurs exactly once;
  direct English failure has no fallback WARN. Normal startup retains
  default logging mode.

- Feature or Constraint: Diagnostic content protection.
  Setup: Use unique sensitive markers in invalid configuration values,
  unknown identifiers, malformed CSV, translation values, and exception
  messages including causes/suppressed exceptions.
  Action: Exercise initialization and fallback failures in both logging
  modes, including stderr fallback.
  Expected: Neither logs nor stderr disclose protected markers or raw
  file content. Safe stages, resource names, counts, line numbers, and
  sanitized exception context remain available.

- Feature or Constraint: No per-lookup logging spam.
  Setup: Initialize development-mode localization and record its startup
  diagnostics.
  Action: Repeat present-key, missing-key, empty-value, and whitespace-only
  value lookups many times.
  Expected: Lookups return the specified results without per-lookup
  diagnostic records or repeated file-fallback warnings.

- Constraint: Resource and file preservation.
  Setup: Use isolated logging/configuration paths and instrument bundled
  resource access. Record current log markers, resource bytes, and existing
  configuration containing recognized unrelated settings.
  Action: Initialize localization with successful selection, fallback,
  and fatal failure; separately normalize the new language setting.
  Expected: Localization reads resources but never creates, edits, or
  replaces them. Existing log contents/ownership are preserved until the
  application shutdown flow closes logging. Only required configuration
  normalization changes TOML; fallback never saves a changed preference,
  and unrelated recognized settings remain intact.

- Constraint: Production-path and fixture isolation.
  Setup: Instrument access to production configuration/logging locations
  and to bundled production localization resources.
  Action: Run automated startup, persistence, malformed-resource, and
  translation-lookup fixtures.
  Expected: Startup/configuration I/O stays in isolated temporary paths.
  Failure/translation fixtures do not alter bundled resources, and test
  translation entries are absent from production resources.

- Constraint: Configuration and localization architecture.
  Setup: Inspect dependencies and exposed types for ApplicationSettings,
  localization consumers, metadata/translation loading, and persistence.
  Action: Verify architectural boundaries and the effective dependency graph.
  Expected: Model defaults/basic validation depend on no CSV, resource
  storage, or OS APIs. Metadata membership is handled by localization.
  Consumers use application-owned APIs; parsing/storage types do not
  escape. Existing Java/Maven/JUnit/logging/configuration dependencies
  are reused; no additional dependency or logging provider is required.

- Feature or Constraint: Existing application behavior.
  Setup: Use valid default localization resources and controlled window
  startup with existing video/window settings.
  Action: Start the application and inspect the existing window-setting
  inputs, lifecycle behavior, and production resources.
  Expected: The window title remains exactly My strategy; black rendering,
  video configuration, and close behavior are unchanged. Localization
  introduces no UI or production translations.

