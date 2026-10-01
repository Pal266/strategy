package com.pidluzsnij.strategy.testsupport;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/** A running {@link ProbeMain} process. */
public final class ProbeProcess {

    private final Process process;
    private final List<String> stdoutLines = new CopyOnWriteArrayList<>();
    private final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
    private final CompletableFuture<Void> stdoutDone;
    private final CompletableFuture<Void> stderrDone;

    private ProbeProcess(Process process) {
        this.process = process;
        this.stdoutDone = CompletableFuture.runAsync(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                    stdoutLines.add(line);
                }
            } catch (IOException ignored) {
                // Process ended.
            }
        });
        this.stderrDone = CompletableFuture.runAsync(() -> {
            try {
                process.getErrorStream().transferTo(stderr);
            } catch (IOException ignored) {
                // Process ended.
            }
        });
    }

    public static ProbeProcess start(Path workingDirectory, Map<String, String> environment, String... args)
            throws IOException {
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(ProbeMain.class.getName());
        command.addAll(List.of(args));
        ProcessBuilder builder = new ProcessBuilder(command).directory(workingDirectory.toFile());
        builder.environment().putAll(environment);
        return new ProbeProcess(builder.start());
    }

    public static ProbeProcess start(Path workingDirectory, String... args) throws IOException {
        return start(workingDirectory, Map.of(), args);
    }

    public void awaitLine(String expected, long timeoutSeconds) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
        while (System.nanoTime() < deadline) {
            if (stdoutLines.contains(expected)) {
                return;
            }
            if (!process.isAlive() && stdoutDone.isDone()) {
                break;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("Probe did not print '" + expected + "'. stdout=" + stdoutLines
                + " stderr=" + stderr());
    }

    public void sendLine() throws IOException {
        OutputStream stdin = process.getOutputStream();
        stdin.write('\n');
        stdin.flush();
    }

    public int awaitExit(long timeoutSeconds) throws Exception {
        if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new AssertionError("Probe did not exit. stdout=" + stdoutLines + " stderr=" + stderr());
        }
        stdoutDone.get(10, TimeUnit.SECONDS);
        stderrDone.get(10, TimeUnit.SECONDS);
        return process.exitValue();
    }

    public void destroy() {
        process.destroyForcibly();
    }

    public List<String> stdout() {
        return stdoutLines;
    }

    public String stderr() {
        synchronized (stderr) {
            return stderr.toString(StandardCharsets.UTF_8);
        }
    }
}
