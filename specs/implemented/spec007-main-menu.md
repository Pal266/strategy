# main-menu

## Type

feature

## Title

main-menu

## Description

Introduce the application's first production UI screen: a simple main menu using the existing UI foundation. The specification supplies the reference menu definition and image assets so implementation work is limited to integrating those resources, extending button-state support, wiring application behavior, localization, and verification.

## Features

- Show the supplied main-menu UI definition automatically after successful application startup.
- Use the supplied `ui/definitions/main-menu.json` as the bundled production main-menu definition and install the supplied image resources under the bundled runtime `ui` resource root without redesigning or substituting them.
- The main-menu logical canvas is 1920 by 1080. The supplied background image occupies the entire logical canvas from `(0, 0)` through `(1920, 1080)` and is rendered behind all menu controls.
- Display four vertically arranged localized buttons in this order: New Game, Load Game, Settings, Exit.
- Extend UI-foundation buttons with an explicit enabled/disabled property and a `disabled` visual state. A disabled button renders its disabled state, does not become hovered or pressed, and cannot emit a semantic activation.
- New Game, Load Game, and Settings are disabled in the supplied main-menu definition.
- Exit is enabled and retains the existing normal, hovered, and pressed interaction behavior.
- Preserve stable semantic behavior identifiers for all four menu buttons: `menu.new_game`, `menu.load_game`, `menu.settings`, and `menu.exit`. Disabled buttons must remain valid definition components even though their behaviors cannot activate.
- Activating `menu.exit` requests normal application termination through the application's normal shutdown path rather than an abnormal or immediate process termination.
- A shutdown initiated by the Exit button performs the application's normal cleanup and produces the existing normal-shutdown completion log event.
- Main-menu presentation remains data-driven through the UI resource system. External per-user resource overrides defined by `ui-foundation` continue to apply to the supplied definition and assets.

## Changes to Existing Behavior

- After successful startup, replace the current production behavior of showing no active UI over the black frame with the main menu as the active UI screen.
- Existing window-close normal shutdown behavior remains unchanged. The Exit button adds another way to request that same normal shutdown outcome.

## Constraints

- UI resource files must not execute application behavior; semantic behavior execution remains controlled by Java application code.
- Disabled buttons must not invoke their semantic behavior even if an external resource changes their visual assets.
- The background must scale only as part of the existing logical-canvas transformation defined by `ui-foundation`; SPEC007 must not introduce independent stretching or aspect-ratio behavior for the background.
- The supplied image assets are normative bundled resources for this specification and must not be regenerated, redesigned, or substituted during implementation.
- No new external dependency is required by this specification.
- The supplied `ui/fonts/main-menu.ttf` is the normative main-menu font: Noto Serif Regular. The implementation agent must bundle this supplied font at that exact runtime UI-resource path and must not choose or substitute another font.
- The supplied Noto Serif Regular font is redistributed under the SIL Open Font License 1.1; the package includes its license notice under `licenses/Noto-Serif-OFL.txt`.

## Blockers

- ui-foundation

## Localization

| Key | English | Ukrainian | Czech | Hungarian |
|---|---|---|---|---|
| `menu.new_game` | New Game | Нова гра | Nová hra | Új játék |
| `menu.load_game` | Load Game | Завантажити гру | Načíst hru | Játék betöltése |
| `menu.settings` | Settings | Налаштування | Nastavení | Beállítások |
| `menu.exit` | Exit | Вийти | Konec | Kilépés |
