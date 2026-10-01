package com.pidluzsnij.strategy.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import ch.qos.logback.core.encoder.Encoder;

import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;

/**
 * Synchronously writes encoded records to the exclusively owned log-file channel.
 * Write failures are reported to {@code stderr}; the file is never reopened.
 */
final class LockedFileAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {

    private final Object writeLock = new Object();
    private final FileChannel channel;
    private final Path file;
    private final FileOperations fileOperations;
    private final PrintStream stderr;
    private Encoder<ILoggingEvent> encoder;

    LockedFileAppender(FileChannel channel, Path file, FileOperations fileOperations, PrintStream stderr) {
        this.channel = channel;
        this.file = file;
        this.fileOperations = fileOperations;
        this.stderr = stderr;
    }

    void setEncoder(Encoder<ILoggingEvent> encoder) {
        this.encoder = encoder;
    }

    @Override
    public void start() {
        if (encoder == null) {
            addError("No encoder set for appender " + name);
            return;
        }
        super.start();
    }

    @Override
    protected void append(ILoggingEvent event) {
        synchronized (writeLock) {
            try {
                byte[] bytes = encoder.encode(event);
                fileOperations.write(channel, ByteBuffer.wrap(bytes));
            } catch (Exception e) {
                stderr.println("Failed to write diagnostic log record to " + file + ": " + e);
            }
        }
    }
}
