package com.pidluzsnij.strategy.window;

import java.util.Objects;

/**
 * Effective startup resolution and its source.
 *
 * @param resolution the resolution the window is created with
 * @param source     where the resolution came from
 */
public record ResolvedResolution(Resolution resolution, ResolutionSource source) {

    public ResolvedResolution {
        Objects.requireNonNull(resolution, "resolution");
        Objects.requireNonNull(source, "source");
    }
}
