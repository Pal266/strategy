# window-title-localization

## Type

feature

## Title

window-title-localization

## Description

Introduce the first production localization key and use it for the
application window title. The title follows the configured startup
language through the existing localization framework.

## Features

- Add the key `application.window.title` exactly once to each of the four
  default localization files in the `localization` resource folder.
  Use the translations in the Localization section below.
- Add these entries to `english.properties`, `ukrainian.properties`,
  `czech.properties`, and `hungarian.properties`, respectively, following
  the framework's UTF-8 `key=value` format. Preserve any existing entries.
- Replace the hardcoded application window title `My strategy` with a
  lookup of `application.window.title` through the initialized localization
  framework. Pass the returned text unchanged as the title when creating
  the window; the first created window must already have that title.
- Use the framework's effective startup language, selected from the
  existing `localization.language` TOML setting. Do not add a separate
  title-language setting or select language from the OS locale.
- With the bundled default resources, English retains the existing title
  `My strategy`; Ukrainian, Czech, and Hungarian use their specified
  translations.
- Preserve the framework's whole-file fallback behavior. If a configured
  non-English localization file is unavailable and English fallback
  succeeds, the title comes from the effective English file. Preserve the
  configured preference and the existing fallback diagnostics.
- If the effective localization file is valid but the title key is
  absent, empty, or whitespace-only, use `application.window.title`
  itself as the window title. Do not substitute the hardcoded English
  text or perform English fallback for an individual key or value.
- If localization initialization fails fatally, do not create a window.
  Preserve the framework's failure diagnostics, failed-startup cleanup,
  and release of logging ownership.
- Resolve the title during startup after localization initialization has
  succeeded and before window creation. A configured-language change
  takes effect on the next application startup; this specification does
  not add runtime language switching or title refresh.
- Preserve the existing successful-window INFO event, reporting the
  resolved window title alongside the existing actual resolution and
  fullscreen state. For this event only, the resolved title is permitted
  diagnostic text, explicitly extending the framework's prohibition on
  logging translation values. Do not add another title-specific log
  event or log other translations, raw localization files, or settings
  snapshots.
- This behavior explicitly replaces the earlier requirements to hardcode
  `My strategy`, to retain that title in every language, and to keep the
  four localization files empty. Those earlier requirements continue to
  describe the behavior before this specification; after implementation,
  the title and resources follow this specification.
- Preserve black window output, video settings, fullscreen/windowed
  selection, resolution handling, responsiveness, close behavior,
  configuration normalization, and all other existing behavior.

## Constraints

- Application code must reference the localization key rather than
  contain the four title translations as runtime strings or fallback
  constants. Test expectations may contain the specified translations.
- Use the existing localization lookup and resource mapping. Do not
  introduce title-specific resource parsing or a second fallback mechanism.
- Add only the production key `application.window.title` in this
  specification. Add no other localized messages, UI controls, metadata
  changes, configuration settings, formatting, or new external dependencies.
- Preserve existing localization resource encoding and file validation,
  configuration and logging ownership, and startup failure policy, except
  for the explicit resource/title changes and diagnostic permission above.
- Update existing tests whose unconditional hardcoded-title or
  empty-localization-file expectations are intentionally replaced here.
  Preserve applicable English-default and unrelated regression checks.
- Automated startup tests must isolate configuration and logging paths;
  failure and translation fixtures must not modify the user's production
  files or bundled production resources.

## Blockers

- localization-framework

## Localization

The previous title was hardcoded `My strategy` and had no localization
key. This specification introduces the following key and translations:

| Key | English | Ukrainian | Czech | Hungarian |
|---|---|---|---|---|
| `application.window.title` | My strategy | Моя стратегія | Moje strategie | Az én stratégiám |

