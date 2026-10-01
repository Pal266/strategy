# application-window Test Scenarios

## Automated Test Scenarios

- Feature: Detected monitor resolution.
  Setup: Supply a starting monitor video mode of 1920×1080.
  Action: Resolve the application resolution.
  Expected: The resolution is 1920×1080.

- Feature: Resolution fallback.
  Setup: Make the starting monitor video mode unavailable.
  Action: Resolve the application resolution.
  Expected: The resolution is 1280×720.

- Feature: Window title and default mode.
  Setup: Initialize the application's initial window settings.
  Action: Inspect the title and startup window mode.
  Expected: The title is exactly `My strategy` and fullscreen is enabled.

- Constraint: Java, Maven, LWJGL, native libraries, and JUnit versions.
  Setup: Open the Maven project and its resolved dependency list.
  Action: Run Maven verification with Java 21 and inspect the effective dependencies and native classifier for the target platform.
  Expected: The project compiles for Java 21, tests execute using JUnit Jupiter 6.1.3, and LWJGL Core, GLFW, OpenGL, and their required natives resolve at 3.4.3.

- Feature or Constraint: Log location and first startup.
  Setup: Use an isolated per-user directory with no strategy subdirectory; observe initialization of other infrastructure.
  Action: Start the application from two different working directories in separate runs.
  Expected: Each run uses the same per-user strategy/log.log location, creates missing directories and the file, and makes logging ready before configuration, GLFW, or graphics initialization.

- Feature or Constraint: Previous log replacement.
  Setup: Place a unique previous-session marker in log.log with no other owner.
  Action: Initialize logging and emit a startup record.
  Expected: The marker is absent and the new startup record is present; no rotation, archive, or other application log file is created.

- Feature or Constraint: Exclusive ownership across processes.
  Setup: Start one process that owns log.log and records a marker.
  Action: Start a second application process targeting the same path, then close the first and start a third.
  Expected: The second reports file-in-use and the absolute path to stderr, aborts before other infrastructure, and does not change the first process's log; the third acquires ownership and replaces the previous contents.

- Feature or Constraint: Initialization failures.
  Setup: Separately simulate location resolution failure, directory creation failure, file opening failure, ownership acquisition failure, and truncation failure.
  Action: Attempt startup for each failure.
  Expected: A descriptive diagnostic goes to stderr, includes the attempted absolute path when available, and no other application infrastructure starts; already-owned files remain unchanged.

- Feature or Constraint: Record format and synchronous delivery.
  Setup: Initialize logging in an isolated directory.
  Action: Emit a record containing non-ASCII text and an exception, then inspect the file after the logging call returns.
  Expected: The record is readable as UTF-8 and contains timestamp with timezone, level, thread, logger, message, exception details, and stack trace.

- Feature or Constraint: Mode filtering and default selection.
  Setup: Use normal startup and separately exercise both modes through a test-controlled selection.
  Action: Emit TRACE, DEBUG, INFO, WARN, and ERROR records.
  Expected: Normal startup selects default; default retains only INFO/WARN/ERROR and development retains all five; no new user setting or startup override is required.

- Feature or Constraint: Default-mode window events.
  Setup: Use controlled window lifecycle and failure paths in each logging mode.
  Action: Exercise startup, successful black-window presentation, unavailable monitor resolution, each initialization-stage failure, a GLFW error callback, cleanup failure, and successful shutdown.
  Expected: Each event has the level and information specified in the main event table; fallback identifies 1280×720; GLFW errors use the logger; failure exceptions appear once; normal termination is absent after failed cleanup.

- Feature or Constraint: Development-mode diagnostics.
  Setup: Supply known runtime, monitor, video-mode, and OpenGL diagnostic values, including an unavailable monitor-value case.
  Action: Exercise successful startup, a close request, and resource cleanup in both modes.
  Expected: Development records the five DEBUG events with all specified details; default excludes them; unavailable values are identified and an unknown close source is not attributed to Alt+F4.

- Feature or Constraint: Shutdown ordering and resource release.
  Setup: Observe cleanup and logging lifecycle during normal shutdown.
  Action: Request closure and then attempt ownership acquisition from a separate process.
  Expected: Resources are cleaned before normal-termination logging; final records are written before logging closes and ownership releases; the next process can acquire ownership.

- Feature or Constraint: Logging write failure during execution.
  Setup: Initialize logging successfully, then simulate a log write failure during execution or cleanup.
  Action: Emit a record and request shutdown.
  Expected: The failure is reported to stderr, existing session records are not erased, and application cleanup is still attempted.

- Feature or Constraint: No event-loop log spam.
  Setup: Initialize development-mode logging and run repeated render and event-loop iterations without lifecycle changes.
  Action: Inspect recorded events.
  Expected: No per-frame, buffer-swap, repeated event-loop, or feature-specific TRACE records are produced.

- Feature or Constraint: Logging dependencies and scope.
  Setup: Inspect the effective Maven dependency graph and startup configuration.
  Action: Verify logging dependencies and mode configuration.
  Expected: SLF4J 2.0.20, Logback Classic/Core 1.6.4, and directories 26 resolve; Logback is the sole SLF4J provider; this feature introduces no user-facing mode control.

## Manual Test Scenarios

- Feature: IntelliJ startup and graphical window.
  Setup: Import the Maven project in IntelliJ IDEA with a Java 21 SDK and a working desktop display.
  Action: Run the main application entry point.
  Expected: A graphical window opens successfully.

- Feature: Title, fullscreen startup, and monitor resolution.
  Setup: Know the current video-mode resolution of the monitor on which the application starts.
  Action: Start the application and inspect its window title, initial mode, and fullscreen output resolution.
  Expected: The title is `My strategy`; the window is fullscreen from startup and uses that monitor's current video-mode resolution.

- Feature: Empty black screen.
  Setup: Start the application.
  Action: Inspect the displayed application output.
  Expected: The output is uniformly black with no menus, controls, text, images, game objects, or other visible application content.

- Feature: Responsive window.
  Setup: Start the application and let it run briefly.
  Action: Switch to another application and return to the window.
  Expected: The window responds to operating-system interaction and still displays black.

- Feature: Alt+F4 shutdown.
  Setup: Focus the running application window.
  Action: Press Alt+F4.
  Expected: The window closes and the application process terminates normally.

- Feature: Normal window close.
  Setup: Start the application.
  Action: Use the operating system's normal window-close action.
  Expected: The window closes and the application process terminates normally.
