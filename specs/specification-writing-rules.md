# Specification Writing Rules

## Purpose

This document defines the rules for writing implementation
specifications for the project.

Each specification must be self-contained enough to describe the
required behavior and to define how completion can be verified.
Specifications should describe **what must be implemented**, not
prescribe unnecessary implementation details.

Each specification must be stored as a Markdown (`.md`) file and have a
companion test-scenarios Markdown file. The two files form one specification
for scope and implementation purposes. The main file defines requirements;
the companion file defines how their completion is verified.

A specification SHOULD represent one cohesive change or capability.
Independent functionality that can be implemented and accepted separately
SHOULD normally be represented by separate specifications.

------------------------------------------------------------------------

## Specification Structure

A specification uses the following sections:

  -----------------------------------------------------------------------
  Section                             Requirement
  ----------------------------------- -----------------------------------
  Type                                Required

  Title                               Required

  Description                         Required

  Features                            Required

  Constraints                         Optional

  Blockers                             Optional

  External Dependencies                Optional

  Localization                        Required when the specification
                                      introduces or changes user-visible
                                      text

  -----------------------------------------------------------------------

Every specification MUST have a companion file in the same directory named
`<title>-test-scenarios.md`, where `<title>` is the main specification's
filename without `.md` and matches its `Title`. The companion MUST exist
before implementation begins. Both files MUST be read together as the active
specification.

Optional sections must be omitted entirely when they have no content. Do
not add empty sections or values such as `None` or `N/A`.

------------------------------------------------------------------------

## Type

`Type` identifies the kind of change requested by the specification.

`Type` MUST be exactly one of the following values:

-   `feature`
-   `bugfix`
-   `refactor`
-   `infrastructure`
-   `test`
-   `documentation`

Choose the value that best describes the primary purpose of the
specification. Other values are not permitted.

------------------------------------------------------------------------

## Title

`Title` is a short identifier for the specification.

Rules:

-   Use lowercase words separated by hyphens.
-   The title must be suitable for use as a Git branch name.
-   Keep it descriptive but concise.

Example:

``` text
main-menu
```

Another example:

``` text
logging-system
```

------------------------------------------------------------------------

## Description

`Description` explains the purpose of the specification.

It should:

-   briefly explain what the specification is intended to accomplish;
-   provide enough context to understand why the requested functionality
    exists;
-   describe the intended result rather than implementation steps.

The description should be brief and must not become a detailed technical
implementation guide. `Description` provides context and intent but MUST NOT
introduce requirements.

------------------------------------------------------------------------

## Features

`Features` contains the functionality that must be implemented.

Rules:

-   Use a list.
-   Each item should describe a distinct required behavior or
    capability.
-   Describe externally meaningful functionality where possible.
-   Do not prescribe classes, methods, or internal implementation unless
    the implementation itself is an explicit requirement.

Example:

``` md
## Features

- Create a log file when the application starts.
- Overwrite the log from the previous application execution.
- Support general and advanced logging modes.
```

------------------------------------------------------------------------

## Changes to Existing Behavior

When a specification intentionally changes existing externally observable
behavior, it MUST explicitly identify the behavior being changed and describe
the required behavior after the change. This includes changes to existing
controls, workflows, UI behavior, configuration, file formats, or game mechanics.

State whether the new behavior replaces the existing behavior or when each
behavior applies. Include the expected result in `Features` and cover the
change with test scenarios in the companion file so the implementation agent can distinguish
an intended change from a regression. If existing behavior should be preserved,
make that boundary explicit where the new feature could otherwise affect it.

Example:

``` md
## Features

- While the pause menu is open, pressing Escape closes it. Outside the pause
  menu, Escape retains its existing behavior of closing the active window.

## Automated Test Scenarios

- Given the pause menu is open, when Escape is pressed, then the menu closes.
- Given another window is open, when Escape is pressed, then that window closes.
```

------------------------------------------------------------------------

## Constraints

`Constraints` defines restrictions or boundaries that the implementation
must respect.

This section is optional and must be omitted when there are no relevant
constraints.

Constraints may define requirements such as:

-   technology restrictions;
-   architectural boundaries;
-   prohibited behavior;
-   runtime requirements;
-   persistence requirements;
-   compatibility requirements.

Example:

``` md
## Constraints

- Translation content must not be hardcoded in application code.
- Changing the active language must not require an application restart.
```

Constraints should define boundaries without unnecessarily prescribing
the internal implementation.

Every objectively verifiable Constraint SHOULD be covered by a scenario in
the companion file or otherwise have an explicit verification mechanism.

------------------------------------------------------------------------

## Blockers

`Blockers` identifies specifications whose features must be fully implemented and
merged into `development` before implementation work on the current specification
begins. The user or workflow controls the order of implementation and ensures
blockers are complete before starting the current specification.

This section is optional and must be omitted when there are no blockers.
List blockers by their specification `Title` so each reference is unambiguous.

Example:

``` md
## Blockers

- localization-system
- ui-foundation
```

Blockers should only be specified when they are genuinely required.
Avoid unnecessary coupling between specifications.

------------------------------------------------------------------------

## External Dependencies

`External Dependencies` lists the external libraries or tools that the
specification requires the implementation to add, replace, or use at a
particular version. This section is optional and must be omitted when no
such dependency choices are required. It is distinct from `Blockers`,
which are other specifications that must be completed first.

For each dependency, specify its name, version, and the capability it is
required to provide. A dependency listed here is authorized when the user
explicitly authorizes implementation of this specification. Existing
project dependencies may be used without listing them unless a specific
choice or version is itself a requirement.

Example:

``` md
## External Dependencies

- ExampleLib 2.1: provide PDF export.
```

If the specified dependencies cannot satisfy the required Features,
Constraints, or test scenarios, the implementation agent must stop
and request a specification update rather than add or substitute a
dependency on its own.

------------------------------------------------------------------------

## Localization

### General Rule

Every user-visible text introduced or changed by a specification must be
localizable.

Application code must reference localization **keys** rather than
hardcoded translated text.

Localization uses key-value pairs. A localization key identifies a piece
of text, and each supported language provides the corresponding value.

Example application usage:

``` java
localization.get("menu.settings");
```

Application code must not contain user-visible translations directly:

``` java
button.setText("Settings");
```

### Required Default Languages

Every specification that introduces or changes user-visible text must
provide translations for all four default languages:

-   English
-   Ukrainian
-   Czech
-   Hungarian

These are the default languages for which project specifications must
provide translations. The localization architecture may support
additional languages in the future.

### Localization Section

When a specification introduces or changes user-visible text, it must
contain a `Localization` section.

The section should provide the localization key and its value in every
default language.

Recommended format:

``` md
## Localization

| Key | English | Ukrainian | Czech | Hungarian |
|---|---|---|---|---|
| `menu.new_game` | New Game | Нова гра | Nová hra | Új játék |
| `menu.load_game` | Load Game | Завантажити гру | Načíst hru | Játék betöltése |
| `menu.settings` | Settings | Налаштування | Nastavení | Beállítások |
| `menu.exit` | Exit | Вийти | Konec | Kilépés |
```

If a specification contains no new or changed user-visible text, the
`Localization` section must be omitted.

### Localization Key Naming

Localization keys should use stable, hierarchical identifiers.

Preferred:

``` text
menu.new_game
menu.load_game
menu.settings
menu.exit

settings.title
settings.general.title
settings.general.language

settings.video.title
settings.video.resolution
settings.video.fullscreen

settings.advanced.title
settings.advanced.logging
```

Avoid identifiers based on UI position or implementation details, such
as:

``` text
SETTINGS_LABEL_3
BUTTON_TEXT_2
LEFT_PANEL_STRING
```

Localization keys should describe the semantic purpose of the text
rather than where or how it is currently displayed.

### Changed Text

When a specification changes existing user-visible wording, it must
identify the existing localization key and provide the new value for all
four default languages.

### Text That Does Not Require Localization

Text intended exclusively for developers or internal application
operation does not require localization unless explicitly required by a
specification.

Examples generally include:

-   log messages;
-   internal configuration keys;
-   class or method names;
-   internal identifiers;
-   developer-only diagnostic messages.

------------------------------------------------------------------------

## Test Scenarios Companion File

The companion file defines verification for the main specification. Its
test scenarios serve as acceptance criteria; the main file MUST NOT
duplicate them.

The companion MUST contain `Automated Test Scenarios` and/or `Manual
Test Scenarios` and MUST have at least one scenario. Omit an empty
section. Each scenario MUST identify the Feature or Constraint it verifies,
state the setup or preconditions, the action to perform, and the observable
expected result. Every Feature MUST be covered by at least one scenario.
One scenario may cover multiple Features, and a Feature may require
multiple scenarios.

Automated scenarios MUST be suitable for an automated test. Every
automated scenario MUST be covered by at least one automated test, and
the test must be run. Manual scenarios MUST provide instructions clear
enough for a person to perform the check and determine whether it passes.
Use manual scenarios only when automation is not reasonably practical;
they remain pending until actually performed. A scenario MUST NOT be
marked passed merely because the implementation appears to satisfy it.

Example companion file for `main-menu.md`:

``` md
# main-menu Test Scenarios

## Automated Test Scenarios

- Feature: Opening the main menu.
  Setup: Start the application.
  Action: Request the main menu.
  Expected: The main menu is shown.

## Manual Test Scenarios

- Feature: Main menu appearance.
  Setup: Open the main menu at the supported display size.
  Action: Inspect the menu.
  Expected: All menu labels are visible without clipping.
```

------------------------------------------------------------------------

## Specification Template

``` md
# <title>

## Type

<type>

## Title

<title>

## Description

<description>

## Features

- <feature>
- <feature>

## Constraints

- <constraint>

## Blockers

- <blocker-title>

## External Dependencies

- <name> <version>: <required capability>

## Localization

| Key | English | Ukrainian | Czech | Hungarian |
|---|---|---|---|---|
| `<key>` | <English value> | <Ukrainian value> | <Czech value> | <Hungarian value> |

```

`Constraints`, `Blockers`, `External Dependencies`, and `Localization` must be removed from
an individual specification when they are not applicable.

Each main specification MUST also have a companion file:

``` md
# <title> Test Scenarios

## Automated Test Scenarios

- Feature or Constraint: <requirement being verified>.
  Setup: <preconditions>.
  Action: <action>.
  Expected: <observable result>.

## Manual Test Scenarios

- Feature or Constraint: <requirement being verified>.
  Setup: <preconditions>.
  Action: <steps for the user>.
  Expected: <observable result>.
```

Omit a scenario section when it has no scenarios; the companion MUST
contain at least one scenario.

------------------------------------------------------------------------

## Guiding Principle

A specification defines:

-   what must be built;
-   what behavior is required;
-   what restrictions must be respected;
-   what localized user-visible text is introduced or changed;
-   how successful implementation can be verified in its companion file.

A specification should generally not dictate:

-   classes to create;
-   methods to create;
-   files to modify;
-   detailed algorithms;
-   step-by-step implementation;
-   internal architecture that is not required by the requested
    behavior.

Project-wide implementation practices, coding standards, testing rules,
Git workflow, dependency policies, and instructions for implementation
agents belong in separate documents.