package com.pidluzsnij.strategy.logging;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * File-system operations used by the logging system. Separated so that each
 * initialization and write failure can be exercised by tests.
 */
public interface FileOperations {

    /**
     * Position of the single byte range locked to claim ownership. It lies far beyond any
     * real log content, so application instances exclude each other while the records
     * themselves stay readable (Windows locks are mandatory for the locked range).
     */
    long OWNERSHIP_LOCK_POSITION = Long.MAX_VALUE - 1;

    /** Operations backed by the real file system. */
    FileOperations SYSTEM = new FileOperations() { };

    default void createDirectories(Path directory) throws IOException {
        Files.createDirectories(directory);
    }

    /** Opens (creating if necessary) the file for writing without altering its contents. */
    default FileChannel open(Path file) throws IOException {
        return FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
    }

    /**
     * Attempts to acquire the exclusive ownership lock of the log file.
     *
     * @return the lock, or {@code null} when another process holds it
     */
    default FileLock tryLock(FileChannel channel) throws IOException {
        return channel.tryLock(OWNERSHIP_LOCK_POSITION, 1, false);
    }

    default void truncate(FileChannel channel) throws IOException {
        channel.truncate(0);
        channel.position(0);
    }

    default void write(FileChannel channel, ByteBuffer data) throws IOException {
        while (data.hasRemaining()) {
            channel.write(data);
        }
    }

    default void release(FileLock lock) throws IOException {
        lock.release();
    }

    default void close(FileChannel channel) throws IOException {
        channel.close();
    }
}
