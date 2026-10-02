package com.pidluzsnij.strategy.ui.resource;

/** Where a resolved UI resource came from. */
public enum UiResourceOrigin {
    /** The application-owned resources bundled on the runtime class path. */
    BUNDLED("bundled resources"),
    /** The user's external override directory. */
    EXTERNAL("external override");

    private final String description;

    UiResourceOrigin(String description) {
        this.description = description;
    }

    /** @return the origin as written to diagnostics */
    public String description() {
        return description;
    }
}
