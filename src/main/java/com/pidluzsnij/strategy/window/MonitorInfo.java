package com.pidluzsnij.strategy.window;

/**
 * Starting monitor information. Absent values are {@code null}.
 *
 * @param name              monitor name, or {@code null} when unavailable
 * @param videoMode         current video mode, or {@code null} when unavailable
 * @param unavailableReason why the video mode is unavailable, or {@code null}
 */
public record MonitorInfo(String name, VideoMode videoMode, String unavailableReason) {

    public static MonitorInfo unavailable(String reason) {
        return new MonitorInfo(null, null, reason);
    }
}
