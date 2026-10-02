# window-title-localization Test Scenarios

## Automated Test Scenarios

- Feature: Production resource entries and preservation.
  Setup: Inspect the bundled default localization resources and record
  unrelated entries that existed before this change, if any.
  Action: Load each of the four files using the localization framework.
  Expected: application.window.title occurs exactly once in each file,
  with the exact corresponding translation from the main specification.
  All files remain valid UTF-8 resources and existing unrelated entries
  are preserved; no other production localization key is introduced.

- Feature: Default English title.
  Setup: Use valid bundled resources and an isolated configuration with
  localization.language absent; initialize logging and configuration
  through their existing defaulting behavior.
  Action: Initialize localization and capture the title passed to the
  controlled window-creation boundary.
  Expected: The effective language is en and the first window-creation
  request has the exact title My strategy. Configuration defaulting
  remains unchanged; no separate title-language setting is added.

- Feature: Configured title in all four languages.
  Setup: Use valid default resources, isolated logging/configuration, and
  each configured language en, uk, cs, and hu in separate runs. Supply an
  OS locale different from the configured language where controllable.
  Action: Initialize localization and capture the first window-creation
  request.
  Expected: Titles are exactly My strategy, Моя стратегія, Moje strategie,
  and Az én stratégiám, respectively. Language follows configuration,
  not OS locale. No initial hardcoded English window is created before
  the localized title is applied.

- Feature or Constraint: Lookup result passed unchanged.
  Setup: Use an isolated selected-language fixture with a distinctive
  nonblank title value containing non-ASCII characters and surrounding
  whitespace.
  Action: Initialize localization and capture the window-creation title.
  Expected: The title exactly matches the framework's returned value,
  including surrounding whitespace, without a hardcoded substitution,
  trimming, formatting, or independent resource parsing.

- Feature: Missing key without individual English fallback.
  Setup: Select a valid non-English fixture that lacks
  application.window.title, while its English fixture contains a usable
  title translation.
  Action: Initialize localization and capture the window-creation title.
  Expected: The title is exactly application.window.title. The effective
  language remains the selected non-English language; no individual
  English lookup or hardcoded fallback occurs.

- Feature: Empty and whitespace-only title values.
  Setup: Select valid fixtures where application.window.title is empty
  or contains only whitespace, including spaces, tabs, and Unicode
  White_Space characters. Give English a usable title in non-English cases.
  Action: Capture the title passed to window creation for each case.
  Expected: Each title is exactly application.window.title. No per-key
  English fallback, language change, fatal failure, or hardcoded English
  substitution occurs. Exercise both effective English and non-English.

- Feature: Whole-file English fallback.
  Setup: Configure a non-English language and make its isolated file
  missing, unreadable, invalid UTF-8, and malformed in separate cases.
  Provide a valid English fixture with application.window.title=My strategy.
  Action: Run startup through the controlled window boundary and inspect
  configuration and diagnostics.
  Expected: Existing framework fallback selects en; the created title is
  My strategy. The configured preference remains unchanged, and the
  inherited fallback diagnostics are not duplicated by title handling.

- Feature: Missing title in English file fallback.
  Setup: Require whole-file English fallback and supply a valid English
  fixture whose title key is absent, empty, or whitespace-only.
  Action: Capture the window-creation title.
  Expected: English fallback succeeds, and the title is exactly
  application.window.title. There is no recursive fallback or embedded
  English title constant.

- Feature: Fatal localization failure prevents window creation.
  Setup: Use isolated startup paths; separately arrange invalid metadata,
  unavailable directly selected English, and unavailable English required
  for fallback.
  Action: Attempt startup and observe window creation, diagnostics,
  cleanup, and log ownership release.
  Expected: No window is created and no successful-window INFO is emitted.
  Existing localization failure handling and cleanup occur, with no
  normal-termination INFO or duplicate failure diagnostic from title handling.

- Feature: Startup order and next-startup language change.
  Setup: Observe controlled logging, configuration, localization, title
  resolution, and window-creation stages. Start with language en, then
  change the isolated persisted preference to uk while the first
  application instance is running.
  Action: Inspect the current instance and then perform a fresh startup.
  Expected: Logging and configuration succeed before localization; title
  lookup follows localization success and precedes creation. The running
  title remains unchanged; the fresh startup uses Моя стратегія. No
  runtime language-switching or title-refresh behavior is introduced.

- Feature or Constraint: Existing successful-window diagnostic.
  Setup: Run successful controlled startup for each default language,
  whole-file fallback, and key-as-title behavior in default and
  development logging modes; supply known actual resolution/fullscreen
  values.
  Action: Inspect the existing successful-window INFO record.
  Expected: It reports the title actually used, plus existing actual
  resolution/fullscreen information, in both modes. There is no additional
  title-specific event, raw localization-file dump, unrelated translation,
  or settings snapshot. Tests for translation-value protection allow only
  the explicitly permitted title in this existing event.

- Feature or Constraint: Window and configuration regression boundaries.
  Setup: Use the existing controlled startup/lifecycle tests and already
  supported video settings, if present.
  Action: Compare startup window parameters and lifecycle behavior while
  varying only localization.language.
  Expected: Only the title changes. Black output, existing fullscreen
  and resolution selection, responsiveness/close handling, and cleanup
  behavior retain their existing requirements. Language selection alone
  causes no unrelated configuration rewrite.

- Constraint: Application-owned lookup and scope.
  Setup: Inspect production title handling, resources, metadata,
  configuration schema, and effective dependencies.
  Action: Verify the localized-title implementation boundary.
  Expected: Application title handling references application.window.title
  through existing application-owned localization access, has no runtime
  translated-title literals or title-specific parser/fallback, and adds
  no other key, setting, UI, metadata change, or external dependency.

- Constraint: Updated earlier expectations and test isolation.
  Setup: Inspect earlier unconditional title/empty-resource tests and
  instrument production-path/resource writes during automated startup cases.
  Action: Run the relevant new and existing regression tests.
  Expected: Earlier expectations are revised only for the explicitly
  changed title/resource behavior; English-default and unrelated checks
  remain. Automated file I/O uses isolated logging/configuration paths,
  and fixture tests modify neither production files nor bundled resources.

## Manual Test Scenarios

- Feature: Actual window title in all four languages.
  Setup: Use a desktop with the application's isolated test launch
  supplying temporary logging/configuration paths and the bundled
  localization resources. Set localization.language in the temporary
  application-settings.toml to en, uk, cs, and hu in separate runs.
  Action: Start each run and inspect the title using the OS window/task
  listing, task switcher, or window title bar when available; then close
  the application before the next run.
  Expected: The OS reports exactly My strategy, Моя стратегія,
  Moje strategie, and Az én stratégiám, respectively, with the non-ASCII
  characters intact.

- Feature: Graphical regression after title localization.
  Setup: Start an isolated desktop test run with a non-English configured
  language and existing supported video/window settings.
  Action: Inspect application output, switch to another application and
  return, then use the existing supported close action.
  Expected: Output remains uniformly black without new application UI;
  the window remains responsive and the process closes normally.

