package com.pidluzsnij.strategy.config.persistence;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/**
 * File-system operations used by configuration persistence. Separated so that each
 * storage failure can be exercised by tests.
 */
public interface StorageOperations {

    /** Operations backed by the real file system. */
    StorageOperations SYSTEM = new StorageOperations() { };

    /** @throws java.nio.file.NoSuchFileException when the file does not exist */
    default byte[] readAllBytes(Path file) throws IOException {
        return Files.readAllBytes(file);
    }

    default void createDirectories(Path directory) throws IOException {
        Files.createDirectories(directory);
    }

    /** Creates a new empty temporary file in {@code directory}. */
    default Path createTempFile(Path directory, String prefix, String suffix) throws IOException {
        return Files.createTempFile(directory, prefix, suffix);
    }

    /** Writes {@code data} to {@code file} and forces it to the storage device. */
    default void writeDurably(Path file, byte[] data) throws IOException {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING)) {
            ByteBuffer buffer = ByteBuffer.wrap(data);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            channel.force(true);
        }
    }

    /** Atomically replaces {@code target} with {@code source}; never falls back to a non-atomic copy. */
    default void moveAtomically(Path source, Path target) throws IOException {
        Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    default void deleteIfExists(Path file) throws IOException {
        Files.deleteIfExists(file);
    }
}
