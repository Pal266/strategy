package com.pidluzsnij.strategy.window;

/** OpenGL context description. Absent values are {@code null}. */
public record GraphicsInfo(String version, String vendor, String renderer) {
}
