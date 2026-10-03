# main-menu Test Scenarios

## Automated Test Scenarios

- Feature: Production main-menu startup.
  Setup: Start the application with valid bundled SPEC007 resources and no external UI overrides.
  Action: Complete normal startup.
  Expected: `ui/definitions/main-menu.json` is loaded as a required production definition and its screen becomes the active UI screen.

- Feature: Supplied resource integration.
  Setup: Inspect the bundled runtime UI resources used by production.
  Action: Resolve the main-menu definition and each image resource referenced by it.
  Expected: The supplied SPEC007 definition and supplied background/button PNG resources are present at their specified UI-resource paths and resolve through the existing UI resource model.

- Feature: Full-canvas background.
  Setup: Load the supplied main-menu definition.
  Action: Inspect its background component and logical layout.
  Expected: The logical layout is 1920x1080; the background starts at 0,0, is 1920x1080, uses the supplied background PNG, is ordered behind the menu controls, and is non-blocking.

- Feature: Localized menu labels.
  Setup: For each supported default language, initialize localization and load the main menu.
  Action: Resolve the four button labels.
  Expected: New Game, Load Game, Settings, and Exit use `menu.new_game`, `menu.load_game`, `menu.settings`, and `menu.exit` and resolve to the translations specified by SPEC007 for that language.

- Feature: Disabled button definition support.
  Setup: Parse a valid button definition containing `enabled: false` and normal, hovered, pressed, and disabled image states.
  Action: Load the definition.
  Expected: The button is accepted as disabled and its disabled image state is available for rendering.

- Feature: Disabled visual state is required.
  Setup: Parse a button definition that omits the disabled image state.
  Action: Load the definition.
  Expected: The definition is rejected rather than partially applied.

- Feature: Disabled buttons are non-interactive.
  Setup: Show a screen containing a disabled button with a valid semantic behavior identifier.
  Action: Move the pointer over it, press it, move away and back, and release over it.
  Expected: It remains visually disabled throughout and emits no semantic activation.

- Feature: Disabled buttons cannot block future semantic registration.
  Setup: Configure `menu.new_game`, `menu.load_game`, `menu.settings`, and `menu.exit` as recognized semantic behavior identifiers and load the supplied main menu.
  Action: Inspect/activate the three disabled controls.
  Expected: The definition is valid, but the disabled controls cannot emit their recognized semantic behaviors.

- Feature: Exit button interaction.
  Setup: Show the supplied main menu.
  Action: Hover, press, and release the Exit button according to the existing activation rules.
  Expected: Exit uses normal/hovered/pressed states as appropriate and emits exactly one `menu.exit` activation only after a valid click release.

- Feature: Exit performs normal shutdown.
  Setup: Run the application with the main menu active and capture lifecycle cleanup and logging.
  Action: Activate `menu.exit`.
  Expected: The application leaves its run loop through the normal shutdown path, performs normal UI/window/GLFW cleanup, and records the existing normal-shutdown completion INFO event.

- Feature: Existing window-close shutdown is preserved.
  Setup: Run the application with the main menu active.
  Action: Request window close without activating Exit.
  Expected: Existing normal window-close shutdown behavior and normal-shutdown completion logging remain unchanged.

- Constraint: UI resources cannot execute behavior.
  Setup: Load the supplied main-menu resources.
  Action: Inspect/parse their declarative content.
  Expected: They contain only supported presentation data and semantic identifiers; Exit execution remains application-controlled.

- Constraint: External override behavior is preserved.
  Setup: Provide a valid external override for one main-menu resource while leaving the other supplied resources absent externally.
  Action: Load the production main menu.
  Expected: The overridden resource comes from the external UI root and each non-overridden resource comes from the bundled SPEC007 resources according to `ui-foundation`.

- Feature: Supplied main-menu font.
  Setup: Inspect the bundled runtime resource corresponding to `ui/fonts/main-menu.ttf`.
  Action: Load the font used by the supplied main-menu definition.
  Expected: The bundled resource is the supplied Noto Serif Regular TTF, and all characters used by the four required language translations have glyphs.

## Manual Test Scenarios

- Feature: Main-menu appearance.
  Setup: Run the application at a supported 16:9 resolution using the supplied resources and a distributable font at `ui/fonts/main-menu.ttf`.
  Action: Inspect the initial screen.
  Expected: The landscape fills the entire logical canvas; four buttons are vertically arranged over it in the supplied layout; New Game, Load Game, and Settings visibly use the muted disabled artwork; Exit uses the enabled artwork; labels are readable and not clipped.

- Feature: Logical-canvas scaling.
  Setup: Run the application at a supported framebuffer aspect ratio different from 16:9.
  Action: Inspect the menu.
  Expected: The background and controls scale and position together through the existing `ui-foundation` logical-canvas transformation without independently stretching the background.
