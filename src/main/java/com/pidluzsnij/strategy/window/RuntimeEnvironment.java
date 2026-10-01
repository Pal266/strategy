package com.pidluzsnij.strategy.window;

/** Runtime environment details recorded for diagnostics. */
public record RuntimeEnvironment(String javaVersion, String osName, String osVersion,
                                 String architecture, String lwjglVersion) {

    public static RuntimeEnvironment current(String lwjglVersion) {
        return new RuntimeEnvironment(
                System.getProperty("java.version"),
                System.getProperty("os.name"),
                System.getProperty("os.version"),
                System.getProperty("os.arch"),
                lwjglVersion);
    }
}
