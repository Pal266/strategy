package com.pidluzsnij.strategy.window;

/** Receives errors reported by GLFW. */
@FunctionalInterface
public interface GlfwErrorListener {
    void onError(int code, String description);
}
