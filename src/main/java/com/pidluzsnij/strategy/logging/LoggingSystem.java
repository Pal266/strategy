package com.pidluzsnij.strategy.logging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;

import java.io.PrintStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static com.pidluzsnij.strategy.logging.LoggingInitializationException.Reason;

/**
 * Synchronous diagnostic logging to a single exclusively owned {@code log.log} file.
 * <p>
 * {@link #initialize} must be called before any other application infrastructure;
 * {@link #close} writes nothing further, detaches the file and releases ownership.
 */
public final class LoggingSystem implements AutoCloseable {

    public static final String DIRECTORY_NAME = "strategy";
    public static final String FILE_NAME = "log.log";

    /** Timestamp with timezone, level, thread, logger, message, then any exception with stack trace. */
    static final String PATTERN =
            "%d{yyyy-MM-dd'T'HH:mm:ss.SSSXXX} %-5level [%thread] %logger - %msg%n%ex{full}";

    private static final String APPENDER_NAME = "log-file";

    private final LoggerContext context;
    private final FileChannel channel;
    private final FileLock lock;
    private final Path file;
    private final FileOperations fileOperations;
    private final PrintStream stderr;
    private boolean closed;

    private LoggingSystem(LoggerContext context, FileChannel channel, FileLock lock, Path file,
                          FileOperations fileOperations, PrintStream stderr) {
        this.context = context;
        this.channel = channel;
        this.lock = lock;
        this.file = file;
        this.fileOperations = fileOperations;
        this.stderr = stderr;
    }

    /**
     * Resolves the log file, acquires exclusive ownership, empties it and routes all
     * SLF4J records at or above the mode's threshold to it.
     */
    public static LoggingSystem initialize(LoggingMode mode, LogLocation location,
                                           FileOperations fileOperations, PrintStream stderr)
            throws LoggingInitializationException {
        Path configDirectory;
        try {
            configDirectory = location.configDirectory();
        } catch (Exception e) {
            throw failure(Reason.LOCATION_UNAVAILABLE, null,
                    "could not determine the per-user configuration directory: " + describe(e), e);
        }
        if (configDirectory == null) {
            throw failure(Reason.LOCATION_UNAVAILABLE, null,
                    "could not determine the per-user configuration directory", null);
        }

        Path directory = configDirectory.toAbsolutePath().resolve(DIRECTORY_NAME);
        Path file = directory.resolve(FILE_NAME);

        try {
            fileOperations.createDirectories(directory);
        } catch (Exception e) {
            throw failure(Reason.DIRECTORY_CREATION_FAILED, file,
                    "could not create log directory " + directory + ": " + describe(e), e);
        }

        FileChannel channel;
        try {
            channel = fileOperations.open(file);
        } catch (Exception e) {
            throw failure(Reason.FILE_OPEN_FAILED, file, "could not open log file: " + describe(e), e);
        }

        // From here on, any failure (including an Error) releases what has been acquired.
        FileLock lock = null;
        boolean initialized = false;
        boolean configuring = false;
        try {
            try {
                lock = fileOperations.tryLock(channel);
            } catch (OverlappingFileLockException e) {
                lock = null;
            } catch (Exception e) {
                throw failure(Reason.OWNERSHIP_ACQUISITION_FAILED, file,
                        "could not acquire exclusive ownership of log file: " + describe(e), e);
            }
            if (lock == null) {
                throw failure(Reason.FILE_IN_USE, file,
                        "log file is already in use by another application instance", null);
            }

            try {
                fileOperations.truncate(channel);
            } catch (Exception e) {
                throw failure(Reason.TRUNCATION_FAILED, file, "could not empty log file: " + describe(e), e);
            }

            LoggingSystem logging;
            configuring = true;
            try {
                LoggerContext context = configure(mode, channel, file, fileOperations, stderr);
                logging = new LoggingSystem(context, channel, lock, file, fileOperations, stderr);
            } catch (RuntimeException e) {
                throw failure(Reason.CONFIGURATION_FAILED, file,
                        "could not configure diagnostic logging: " + describe(e), e);
            }
            initialized = true;
            return logging;
        } finally {
            if (!initialized) {
                if (configuring) {
                    detachQuietly();
                }
                if (lock != null) {
                    releaseQuietly(fileOperations, lock);
                }
                closeQuietly(fileOperations, channel);
            }
        }
    }

    /** @return the absolute path of the owned log file */
    public Path file() {
        return file;
    }

    /**
     * Stops accepting records, then releases exclusive ownership and closes the file.
     * Records logged before this call are already in the file.
     */
    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        context.reset();
        try {
            fileOperations.release(lock);
        } catch (Exception e) {
            stderr.println("Failed to release ownership of log file " + file + ": " + e);
        }
        try {
            fileOperations.close(channel);
        } catch (Exception e) {
            stderr.println("Failed to close log file " + file + ": " + e);
        }
    }

    private static LoggerContext configure(LoggingMode mode, FileChannel channel, Path file,
                                           FileOperations fileOperations, PrintStream stderr) {
        ILoggerFactory factory = LoggerFactory.getILoggerFactory();
        if (!(factory instanceof LoggerContext context)) {
            throw new IllegalStateException("Logback is not the active SLF4J provider: " + factory.getClass().getName());
        }
        context.reset();

        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setContext(context);
        encoder.setPattern(PATTERN);
        encoder.setCharset(StandardCharsets.UTF_8);
        encoder.start();

        LockedFileAppender appender = new LockedFileAppender(channel, file, fileOperations, stderr);
        appender.setContext(context);
        appender.setName(APPENDER_NAME);
        appender.setEncoder(encoder);
        appender.start();
        if (!appender.isStarted()) {
            throw new IllegalStateException("log file appender failed to start");
        }

        Logger root = context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        root.setLevel(mode.threshold());
        root.addAppender(appender);
        return context;
    }

    private static LoggingInitializationException failure(Reason reason, Path file, String detail, Throwable cause) {
        StringBuilder message = new StringBuilder("Startup aborted: diagnostic logging could not be initialized: ")
                .append(detail);
        if (file != null) {
            message.append(" (log file: ").append(file).append(')');
        }
        return new LoggingInitializationException(reason, file, message.toString(), cause);
    }

    private static String describe(Throwable e) {
        String message = e.getMessage();
        return message == null ? e.getClass().getName() : e.getClass().getSimpleName() + ": " + message;
    }

    /** Removes any partially configured appender so nothing writes to the closed file. */
    private static void detachQuietly() {
        try {
            if (LoggerFactory.getILoggerFactory() instanceof LoggerContext context) {
                context.reset();
            }
        } catch (RuntimeException ignored) {
            // Already failing; the original failure is reported.
        }
    }

    private static void releaseQuietly(FileOperations fileOperations, FileLock lock) {
        try {
            fileOperations.release(lock);
        } catch (Exception ignored) {
            // Already failing; the original failure is reported.
        }
    }

    private static void closeQuietly(FileOperations fileOperations, FileChannel channel) {
        try {
            fileOperations.close(channel);
        } catch (Exception ignored) {
            // Already failing; the original failure is reported.
        }
    }
}
