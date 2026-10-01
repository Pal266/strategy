package com.pidluzsnij.strategy;

import com.pidluzsnij.strategy.logging.DirectoriesLogLocation;
import com.pidluzsnij.strategy.logging.FileOperations;
import com.pidluzsnij.strategy.logging.LogLocation;
import com.pidluzsnij.strategy.logging.LoggingInitializationException;
import com.pidluzsnij.strategy.logging.LoggingMode;
import com.pidluzsnij.strategy.logging.LoggingSystem;
import com.pidluzsnij.strategy.window.Application;
import com.pidluzsnij.strategy.window.LwjglWindowSystem;
import com.pidluzsnij.strategy.window.RuntimeEnvironment;
import com.pidluzsnij.strategy.window.WindowSystem;
import org.lwjgl.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintStream;
import java.util.function.Supplier;

/**
 * Starts the application: diagnostic logging first, then all other infrastructure,
 * and closes logging last.
 */
public final class ApplicationLauncher {

    /** Logging mode selected for normal application startup. */
    public static final LoggingMode NORMAL_STARTUP_MODE = LoggingMode.DEFAULT;

    private final LoggingMode mode;
    private final LogLocation logLocation;
    private final FileOperations fileOperations;
    private final PrintStream stderr;
    private final Supplier<WindowSystem> windowSystemFactory;
    private final Supplier<RuntimeEnvironment> runtimeFactory;

    public ApplicationLauncher(LoggingMode mode, LogLocation logLocation, FileOperations fileOperations,
                               PrintStream stderr, Supplier<WindowSystem> windowSystemFactory,
                               Supplier<RuntimeEnvironment> runtimeFactory) {
        this.mode = mode;
        this.logLocation = logLocation;
        this.fileOperations = fileOperations;
        this.stderr = stderr;
        this.windowSystemFactory = windowSystemFactory;
        this.runtimeFactory = runtimeFactory;
    }

    /** The launcher used by normal application startup. */
    public static ApplicationLauncher forNormalStartup() {
        return new ApplicationLauncher(NORMAL_STARTUP_MODE, new DirectoriesLogLocation(), FileOperations.SYSTEM,
                System.err, LwjglWindowSystem::new, () -> RuntimeEnvironment.current(Version.getVersion()));
    }

    /** @return the logging mode this launcher initializes */
    public LoggingMode mode() {
        return mode;
    }

    /** @return the process exit code */
    public int launch() {
        LoggingSystem logging;
        try {
            logging = LoggingSystem.initialize(mode, logLocation, fileOperations, stderr);
        } catch (LoggingInitializationException e) {
            stderr.println(e.getMessage());
            return Application.EXIT_FAILURE;
        }
        try {
            Logger log = LoggerFactory.getLogger(ApplicationLauncher.class);
            log.info("Application startup begins (logging mode: {})", mode.id());
            Application application;
            try {
                application = new Application(windowSystemFactory.get(), runtimeFactory.get());
            } catch (RuntimeException | LinkageError e) {
                log.error("Application infrastructure could not be created", e);
                return Application.EXIT_FAILURE;
            }
            return application.run();
        } finally {
            logging.close();
        }
    }
}
