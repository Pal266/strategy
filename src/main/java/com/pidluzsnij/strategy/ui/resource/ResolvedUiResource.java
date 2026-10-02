package com.pidluzsnij.strategy.ui.resource;

import java.util.Objects;

/** The bytes of a resolved UI resource and where they came from. */
public final class ResolvedUiResource {

    private final UiResourcePath path;
    private final UiResourceOrigin origin;
    private final byte[] bytes;

    public ResolvedUiResource(UiResourcePath path, UiResourceOrigin origin, byte[] bytes) {
        this.path = Objects.requireNonNull(path, "path");
        this.origin = Objects.requireNonNull(origin, "origin");
        this.bytes = bytes.clone();
    }

    public UiResourcePath path() {
        return path;
    }

    public UiResourceOrigin origin() {
        return origin;
    }

    /** @return a copy of the resource's complete contents */
    public byte[] bytes() {
        return bytes.clone();
    }

    /** @return the resource name used in diagnostics */
    public String name() {
        return origin == UiResourceOrigin.EXTERNAL ? "external " + path : "bundled " + path;
    }

    /** Deliberately omits the contents. */
    @Override
    public String toString() {
        return "ResolvedUiResource[" + name() + ", " + bytes.length + " bytes]";
    }
}
