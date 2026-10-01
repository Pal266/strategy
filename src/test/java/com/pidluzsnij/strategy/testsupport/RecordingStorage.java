package com.pidluzsnij.strategy.testsupport;

import com.pidluzsnij.strategy.config.persistence.StorageOperations;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** {@link StorageOperations} that records every operation and can fail at any of them. */
public class RecordingStorage implements StorageOperations {

    /** Operation names, for example {@code read}, followed by the path, as {@code "read <path>"}. */
    public final List<String> events;
    public final List<Path> paths = Collections.synchronizedList(new ArrayList<>());

    public IOException readFailure;
    public IOException createDirectoriesFailure;
    public IOException createTempFailure;
    public IOException writeFailure;
    public IOException moveFailure;

    public RecordingStorage() {
        this(Collections.synchronizedList(new ArrayList<>()));
    }

    public RecordingStorage(List<String> events) {
        this.events = events;
    }

    private void record(String operation, Path path) {
        events.add("config-" + operation + " " + path);
        paths.add(path);
    }

    public long count(String operation) {
        return events.stream().filter(e -> e.startsWith("config-" + operation + " ")).count();
    }

    @Override
    public byte[] readAllBytes(Path file) throws IOException {
        record("read", file);
        if (readFailure != null) {
            throw readFailure;
        }
        return StorageOperations.super.readAllBytes(file);
    }

    @Override
    public void createDirectories(Path directory) throws IOException {
        record("createDirectories", directory);
        if (createDirectoriesFailure != null) {
            throw createDirectoriesFailure;
        }
        StorageOperations.super.createDirectories(directory);
    }

    @Override
    public Path createTempFile(Path directory, String prefix, String suffix) throws IOException {
        record("createTempFile", directory);
        if (createTempFailure != null) {
            throw createTempFailure;
        }
        Path file = StorageOperations.super.createTempFile(directory, prefix, suffix);
        paths.add(file);
        return file;
    }

    @Override
    public void writeDurably(Path file, byte[] data) throws IOException {
        record("write", file);
        if (writeFailure != null) {
            throw writeFailure;
        }
        StorageOperations.super.writeDurably(file, data);
    }

    @Override
    public void moveAtomically(Path source, Path target) throws IOException {
        record("move", target);
        paths.add(source);
        if (moveFailure != null) {
            throw moveFailure;
        }
        StorageOperations.super.moveAtomically(source, target);
    }

    @Override
    public void deleteIfExists(Path file) throws IOException {
        record("delete", file);
        StorageOperations.super.deleteIfExists(file);
    }
}
