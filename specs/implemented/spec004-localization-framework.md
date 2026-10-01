# localization-framework

## Type

infrastructure

## Title

localization-framework

## Description

Establish localization resources, language selection, and key lookup for
future application features. The framework integrates with existing
configuration and startup infrastructure without translating existing
application text or introducing localization controls.

## Features

### Resource metadata and initial language files

- Provide one application-owned localization resource folder named
  `localization`, containing one language metadata file named
  `languages.csv` and the localization files referenced by that metadata.
- Bundle these resources with the application. Resource access must work
  when running the Maven project from IntelliJ and when resources are
  loaded from the application's runtime classpath; it must not depend on
  the process working directory or a source-tree filesystem path.
- Store metadata as UTF-8 CSV with exactly these header fields in this
  order: `language_identifier,displayed_name,localization_file`.
- The initial metadata must contain these four language entries in this
  order:

  | language_identifier | displayed_name | localization_file |
  |---|---|---|
  | `en` | English | `english.properties` |
  | `uk` | Українська | `ukrainian.properties` |
  | `cs` | Čeština | `czech.properties` |
  | `hu` | Magyar | `hungarian.properties` |

- Provide all four referenced localization files as empty UTF-8 files.
  An empty localization file is valid. This specification adds no
  production translation keys or translated application messages.
- Treat metadata as the source of available languages and their resource
  mappings. Additional valid metadata entries are supported without
  introducing another hardcoded identifier-to-file mapping. The four
  initial entries, including the English default entry, remain required.
- Expose the language identifiers and display names to application
  consumers for later features, preserving metadata order. This
  specification does not display those names in the application's UI.
- Metadata has one record per language and exactly three fields per
  record. Support comma-separated fields and quoted fields, including
  commas inside quotes and doubled quotes representing a literal quote.
  Quoted fields must not span multiple physical lines. Accept LF and CRLF
  line endings and ignore empty physical lines outside records.
- All metadata field values must be nonempty and must not contain leading
  or trailing whitespace. Language identifiers are matched exactly and
  case-sensitively and must be unique. Display names need not be unique.
  Referenced localization filenames must also be unique.
- Each localization filename must be a simple filename ending in
  `.properties`, resolved within the same localization folder. Absolute
  paths, directory separators, and traversal components are invalid.
- Missing, unreadable, invalid UTF-8, or malformed metadata is a fatal
  localization initialization failure. Malformation includes an invalid
  header, wrong field count, invalid quoting, invalid field values,
  duplicate language identifiers or filenames, or absence of any required
  initial language entry. Do not silently discard malformed records or
  select a language using an untrusted or partial metadata result.
- Permit automated tests to supply isolated localization resource sets,
  including missing and malformed resources, without changing the
  application's bundled production resources.

### Language configuration

- Add a production `localization.language` setting to ApplicationSettings
  and persist it in the existing `application-settings.toml`.
- The setting accepts a nonempty, non-whitespace-only TOML string with no
  leading or trailing whitespace and defaults to `"en"`. Do not coerce
  booleans, numbers, arrays, or tables into language identifiers.
- A missing or invalid setting receives `"en"` through existing
  configuration normalization and complete-snapshot persistence.
  Configuration that predates this setting is extended with the default.
- If a parsed `localization` section is present but is not a table,
  treat `localization.language` as invalid, replace the section with its
  recognized default settings, and emit one configuration WARN identifying
  `localization.language`. A missing section is a missing-setting case.
  Syntactically malformed TOML retains configuration's fatal load policy.
- The default persisted representation is:

  ```toml
  [localization]
  language = "en"
  ```

- Preserve every other recognized application setting during normalization.
  A complete valid configuration containing this setting must not be
  rewritten solely because localization starts.
- Select the startup language using the effective configured identifier
  after configuration initialization succeeds. Do not select a language
  from the OS locale or introduce another application configuration file.
- Match the configured identifier against metadata exactly. A well-formed
  identifier that has no metadata entry is unavailable: select English as
  the fallback and produce the fallback WARN defined below. This is a
  localization selection outcome, not a configuration type-validation
  failure.
- Keep configured and effective language identifiers distinct. Unknown
  identifiers and resource fallback must not rewrite an otherwise valid
  configured preference. Missing or invalid configuration values still
  persist the default through configuration normalization as described
  above.

### Localization file format

- Each localization file is UTF-8 text containing zero or more
  `key=value` records, one per physical line. Accept LF and CRLF line endings.
- This is a line-oriented subset of properties-file syntax. Blank lines
  and lines whose first non-whitespace character is `#` are ignored.
  Other lines must contain `=`; the first `=` separates key from value,
  and later `=` characters belong to the value.
- Remove leading and trailing whitespace from the key. A resulting empty
  key is invalid. Keys are case-sensitive and must be unique after that
  whitespace removal.
- Preserve the value exactly as written after the first `=`, including
  leading or trailing whitespace. Do not interpret inline comments,
  escape sequences, continuation lines, placeholders, or formatting
  expressions. Literal non-ASCII characters are supported directly.
- A present key with an empty or whitespace-only value is valid file
  syntax but has no usable localized value. For whitespace checks in this
  specification, whitespace means characters in the Unicode White_Space
  property.
- A resource is unavailable if it is missing, unreadable, invalid UTF-8,
  or malformed. A non-comment record without `=`, an empty key, and
  duplicate keys make the entire file malformed. Do not partially load a
  malformed file or silently use the last duplicate value.

### Language selection, fallback, and lookup

- Load the selected language file during localization initialization.
  Read failures and parse failures follow the same fallback policy as a
  missing file.
- When a non-English configured language has a usable metadata entry and
  a successfully loaded file, use that language. English does not need to
  be loaded or available in this case.
- If the selected non-English language is unavailable, attempt to load
  the English file referenced by the `en` metadata entry.
- If English is successfully loaded as the fallback, set the effective
  language to `en`, continue startup, and preserve the configured
  preference.
- If English is selected initially and its file is unavailable, or if
  English fallback is required and its file is unavailable, log an ERROR
  and terminate the failed startup using the cleanup policy below. Do not
  retry English recursively.
- File existence and validity for nonselected languages must not block
  startup. Validate metadata globally, but load only the selected file
  and English when needed as its fallback.
- Provide application-owned lookup access that takes a requested key and
  uses the effective language selected from configuration. Consumers must
  not require CSV, properties-parser, or resource-storage types.
- When the effective language contains the requested key with a nonempty,
  non-whitespace-only value, return that value exactly.
- When the requested key is absent, or its value is empty or
  whitespace-only, return the requested key itself unchanged.
- Explicitly do not fall back to English for individual missing keys or
  missing values. This also applies when English contains a usable value
  for that same key.
- A successfully loaded empty file remains a valid selected language.
  Its lookups return the requested keys; it must not trigger English
  fallback or fatal startup.
- Load and retain the effective language's entries for lookup. Repeated
  lookups must perform no resource reads or configuration writes.
  Changes to metadata, files, or the configured language take effect on
  the next application startup; runtime reload and language switching are
  outside this specification.
- Lookup keys supplied by callers must be non-null, nonempty, and not
  whitespace-only. Invalid lookup arguments must be rejected as
  programming errors without selecting a fallback language or initiating
  application shutdown. A valid requested key is matched exactly and is
  not trimmed.

### Startup integration and diagnostic logging

- Initialize localization only after logging and application configuration
  initialization have both succeeded, and before GLFW, graphics, or window
  initialization. Failure of either earlier stage prevents localization
  initialization.
- Continue window startup only after localization initialization succeeds.
  Preserve existing window title, black output, video settings, and close
  behavior. In particular, do not translate or replace `My strategy` in
  this specification.
- Use existing SLF4J/Logback logging, its selected mode, and its log-file
  ownership. Do not recreate, truncate, replace, or independently close
  the active log file.
- Reuse configuration's diagnostics for missing or invalid
  `localization.language`: missing defaults use DEBUG; invalid values use
  WARN. Localization must not duplicate those normalization events.
- Record the following localization events:

  | Event | Level | Required information |
  |---|---|---|
  | Localization initialization succeeds | INFO | Effective language identifier; whether English fallback was used |
  | Configured language is unavailable and English fallback is selected | WARN | Unavailable-language reason; fallback identifier `en`; selected resource name when safely available |
  | Localization initialization fails | ERROR | Failed stage; safe resource name when available; safe failure details and stack trace when available |
  | Language metadata is successfully loaded | DEBUG | Metadata resource name; number of language entries |
  | A localization file is successfully loaded | DEBUG | Effective language identifier; resource name; number of keys |

- Emit success events only after the relevant load or full initialization
  succeeds. The fallback WARN is emitted once per fallback selection,
  including when the subsequent English load fails. Direct failure of a
  configured English file produces ERROR without a redundant fallback WARN.
- INFO, WARN, and ERROR use the inherited default-mode policy. DEBUG is
  included only in development mode; this feature introduces no new
  logging mode or mode-selection setting.
- Do not log translation values, whole metadata records, raw CSV or
  properties content, raw invalid configuration values, or complete
  configuration snapshots. Sanitize exception messages and nested
  exception information if they contain such content. Fixed resource
  names, validated language identifiers, counts, and line numbers are
  permitted diagnostic context. An unknown unvalidated identifier must
  not be echoed as a raw configuration value.
- Missing-key and missing-value lookup results require no log records.
  Repeated lookups must not produce per-lookup diagnostic spam.
- A fatal localization initialization failure prevents GLFW/window
  startup, records the failure once, attempts application cleanup, closes
  logging, releases log-file ownership, and terminates the process with
  a failure outcome. Do not emit normal-termination INFO for failed startup.
  Logging failures use the inherited stderr fallback and must not prevent
  cleanup or ownership-release attempts.

## Constraints

- This specification introduces localization infrastructure and the
  concrete language configuration setting only. Add no production
  translation entries, translated UI, language-selection controls, or
  migration of existing hardcoded application text.
- Keep language setting defaults and basic value validation in
  ApplicationSettings, independent of CSV, translation-file parsing,
  resource paths, and the operating system. Metadata membership and file
  availability are localization-framework concerns.
- Confine metadata and translation-file parsing and storage access to
  localization infrastructure; application consumers use application-owned
  language information and lookup APIs.
- Reuse Java 21, Maven, existing logging, configuration persistence, atomic
  saving, and existing JUnit Jupiter tooling. No additional external
  dependency is required by this specification.
- Bundled localization resources are read-only at runtime. The framework
  must not create missing localization files or metadata, edit translations,
  or save resource fallback as a changed configured language.
- Startup and persistence tests must isolate both logging and configuration
  paths and must not access the user's production files. Localization tests
  use isolated resource fixtures; test-only translation entries must not
  enter bundled production resources.
- Existing configuration failure handling and preservation of unrelated
  settings and active log records remain applicable.

## Blockers

- configuration

