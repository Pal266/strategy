package com.pidluzsnij.strategy.config.persistence;

import java.nio.file.Path;
import java.util.Objects;

/**
 * A configuration persistence operation failed. Messages and causes identify the operation
 * and file but never carry configuration contents.
 */
public final class ConfigurationPersistenceException extends Exception {

    /** The persistence operation that failed. */
    public enum Operation {
        READ("read configuration file"),
        PARSE("parse configuration file"),
        CREATE_DIRECTORY("create configuration directory"),
        WRITE("write configuration file"),
        REPLACE("replace configuration file");

        private final String description;

        Operation(String description) {
            this.description = description;
        }

        /** @return a short developer-facing description of the operation */
        public String description() {
            return description;
        }
    }

    private final Operation operation;
    private final Path file;

    public ConfigurationPersistenceException(Operation operation, Path file, String detail, Throwable cause) {
        super(message(operation, file, detail), cause);
        this.operation = Objects.requireNonNull(operation, "operation");
        this.file = file;
    }

    private static String message(Operation operation, Path file, String detail) {
        StringBuilder message = new StringBuilder("Could not ").append(operation.description());
        if (detail != null && !detail.isBlank()) {
            message.append(": ").append(detail);
        }
        if (file != null) {
            message.append(" (configuration file: ").append(file).append(')');
        }
        return message.toString();
    }

    public Operation operation() {
        return operation;
    }

    /** @return the configuration file path, or {@code null} when unknown */
    public Path file() {
        return file;
    }
}
