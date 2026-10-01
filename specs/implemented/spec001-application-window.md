# application-window

## Type

feature

## Title

application-window

## Description

Establish the first runnable desktop application for the strategy game. On startup, it presents an empty black fullscreen window sized for the starting monitor. Local diagnostic logging captures startup, window lifecycle, and failures.

## Features

- The application starts by running its main entry point in IntelliJ IDEA from the Maven project.
- Startup opens a graphical window titled exactly `My strategy`.
- The window displays a uniformly black screen without other visible application content.
- The application uses the current video-mode resolution of its starting monitor as its application resolution when that resolution can be determined.
- If the starting monitor's resolution cannot be determined, the application uses 1280×720 as its application resolution.
- The window opens in fullscreen mode by default.
- The window stays responsive and continues displaying black until closed.
- Pressing Alt+F4 closes the application and terminates its process normally.
- Closing the window by the operating system's normal close action terminates the process normally.

### Logging lifecycle and output

- Before configuration, GLFW, graphics, or any other application infrastructure initializes, determine the log-file location and initialize synchronous diagnostic logging.
- Use exactly one application log file named `log.log`, located in the `strategy` subdirectory of the operating system's conventional per-user configuration directory. Resolve this location independently of configuration loading and the process working directory; create the directory if necessary.
- Acquire exclusive ownership of the log file before modifying its contents. Retain ownership until logging is closed during shutdown. Only one application instance may use that file at a time.
- After exclusive ownership is acquired, empty the existing file or create an empty file, and make it ready for writing before any other application infrastructure starts. Previous-session log records are replaced on each successful logging initialization.
- If the location cannot be determined, the directory or file cannot be created or opened, exclusive ownership cannot be acquired, or truncation fails, report the reason to `stderr` and abort startup before other infrastructure initializes. Include the attempted absolute file path when available; distinguish a file already in use from other initialization failures. An instance rejected because the file is in use must not alter the existing file.
- Write records in UTF-8 with a timestamp including timezone, severity level, thread name, logger name, and message. Include exception details and stack traces when required by the event below. Diagnostic wording need not match an exact string.
- Support `default` and `development` logging modes. `default` accepts INFO, WARN, and ERROR records; `development` additionally accepts DEBUG and TRACE records.
- Select `default` for normal application startup. Development mode must be verifiable without adding a user-facing setting, configuration-file key, environment-variable override, or command-line option in this feature.
- Log the following events at their specified levels. Development mode includes all default-mode events:

  | Event | Level | Required information |
  |---|---|---|
  | Application startup begins, after logging is ready | INFO | Selected logging mode |
  | Window successfully opens and displays black | INFO | Window title, actual resolution, actual fullscreen state |
  | Starting monitor resolution cannot be determined | WARN | Reason when available; fallback resolution `1280×720` |
  | GLFW initialization, window creation, or OpenGL initialization fails | ERROR | Failed stage; exception and stack trace when available |
  | GLFW reports an error | ERROR | GLFW error code and description |
  | Application cleanup fails | ERROR | Failed cleanup operation; exception and stack trace |
  | Application terminates normally, after successful cleanup | INFO | Normal shutdown completed |
  | Runtime environment identified | DEBUG | Java version, OS name and version, architecture, LWJGL version |
  | Starting monitor and video mode identified | DEBUG | Monitor name, resolution, refresh rate; explicitly identify unavailable values |
  | OpenGL context initialized | DEBUG | OpenGL version, vendor, renderer |
  | Window close request observed | DEBUG | Close request received |
  | Window and graphics resources released | DEBUG | Cleanup completed |

- Route GLFW error-callback diagnostics through this logging system, replacing their existing direct `stderr` output after logging is initialized.
- Record a failure's exception and stack trace once, rather than repeating it at multiple handling layers. A separate GLFW native diagnostic may accompany that failure.
- Record normal termination only after application cleanup succeeds. Write final records before closing logging and releasing exclusive ownership.
- If writing to the initialized log file fails during execution, report the failure to `stderr`; diagnostic logging failure must not prevent application cleanup. Do not reopen the file in a way that erases records from the current execution.

## Constraints

- Use Java 21 and Maven.
- Use SLF4J as the application logging API and Logback as its sole SLF4J provider.
- Use synchronous file logging without rotation, archives, or additional application log files. Normal diagnostic records go to the file; stderr is the fallback for logging-system failures.
- Do not log individual frames, buffer swaps, or repeated event-loop iterations. This feature requires no TRACE events.
- Do not attribute a close request to Alt+F4 or a particular OS action unless the application can reliably determine its source.
- Logging additions preserve the existing window behavior except that logging initialization or ownership failure now prevents startup.
- Logging messages and stderr diagnostics are developer diagnostics and do not require localization. Configuration-specific log events and user-configurable mode selection are outside this feature; future features define their own events and severity levels.
- Use LWJGL 3.4.3 with its GLFW and OpenGL bindings for windowing and the black graphics output.
- Supply native LWJGL libraries appropriate for the operating system on which the application is run.
- Hardcode `My strategy` as the window title for this initial feature; no title localization or configuration is required yet.
- Do not display menus, controls, text, images, game objects, or other application content in the initial window.
- Use JUnit Jupiter 6.1.3 for automated tests run by Maven.

## External Dependencies

- SLF4J 2.0.20 (`org.slf4j:slf4j-api`): provide the application logging API.
- Logback Classic 1.6.4 (`ch.qos.logback:logback-classic`, with matching transitive `logback-core`): provide synchronous UTF-8 file logging and severity filtering.
- directories 26 (`dev.dirs:directories`): resolve the operating system's conventional per-user configuration directory for the log location.
- LWJGL Core 3.4.3 (`org.lwjgl:lwjgl`): provide the core runtime.
- LWJGL GLFW 3.4.3 (`org.lwjgl:lwjgl-glfw`): provide monitor, window, fullscreen, and window-event access.
- LWJGL OpenGL 3.4.3 (`org.lwjgl:lwjgl-opengl`): provide the graphics access for the black screen.
- LWJGL 3.4.3 native artifacts for the target operating system (`org.lwjgl:lwjgl`, `org.lwjgl:lwjgl-glfw`, and `org.lwjgl:lwjgl-opengl` with appropriate native classifiers): provide native runtime libraries.
- JUnit Jupiter 6.1.3 (`org.junit.jupiter:junit-jupiter`): provide automated test APIs and engine.
