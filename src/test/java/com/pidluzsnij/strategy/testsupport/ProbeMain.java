package com.pidluzsnij.strategy.testsupport;

import com.pidluzsnij.strategy.ApplicationLauncher;
import com.pidluzsnij.strategy.config.ApplicationSettings;
import com.pidluzsnij.strategy.logging.DirectoriesLogLocation;
import com.pidluzsnij.strategy.logging.FileOperations;
import com.pidluzsnij.strategy.logging.LoggingMode;
import com.pidluzsnij.strategy.window.RuntimeEnvironment;
import com.pidluzsnij.strategy.window.WindowSystem;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

/**
 * Separate-process application used by multi-process tests.
 * <p>
 * Usage: {@code ProbeMain location} prints the production per-user configuration directory;
 * {@code ProbeMain run|hold <configDir> <marker>} launches the application with a fake window
 * system, using {@code configDir} for both the log and the configuration file. {@code hold}
 * prints {@code READY} once running and closes after a line on stdin.
 */
public final class ProbeMain {

    private ProbeMain() {
    }

    public static void main(String[] args) throws Exception {
        if (args[0].equals("location")) {
            System.out.println("LOCATION " + new DirectoriesLogLocation().configDirectory());
            return;
        }
        boolean hold = args[0].equals("hold");
        Path configDirectory = Path.of(args[1]);
        String marker = args[2];
        Path logFile = configDirectory.resolve("strategy").resolve("log.log");

        FakeWindowSystem windowSystem = new FakeWindowSystem();
        windowSystem.onInitialize = () -> LoggerFactory.getLogger("probe").info("MARKER {}", marker);
        if (hold) {
            windowSystem.iterationsBeforeClose = Integer.MAX_VALUE;
            windowSystem.onProcessEvents = () -> {
                System.out.println("READY");
                System.out.flush();
                try {
                    new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)).readLine();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                windowSystem.requestClose();
            };
        }
        Supplier<WindowSystem> factory = () -> {
            try {
                System.out.println("INFRA_STARTED logSize=" + Files.size(logFile));
            } catch (IOException e) {
                System.out.println("INFRA_STARTED logSize=missing");
            }
            System.out.flush();
            return windowSystem;
        };
        int exitCode = new ApplicationLauncher(LoggingMode.DEFAULT, () -> configDirectory, FileOperations.SYSTEM,
                System.err, ApplicationSettings.SCHEMA, () -> configDirectory, ApplicationLauncher.TOML_PERSISTENCE,
                factory, () -> RuntimeEnvironment.current("probe")).launch();
        System.out.println("EXIT " + exitCode);
        System.out.flush();
        System.exit(exitCode);
    }
}
