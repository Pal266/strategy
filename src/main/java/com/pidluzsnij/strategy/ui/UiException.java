package com.pidluzsnij.strategy.ui;

/**
 * A UI-foundation failure with safe diagnostic context: the stage that failed, the UI resource
 * involved (when any) and a reason that never contains raw resource content or translated text.
 */
public class UiException extends Exception {

    private final String stage;
    private final String resource;
    private final String reason;

    public UiException(String stage, String resource, String reason, Throwable cause) {
        super(describe(stage, resource, reason), cause);
        this.stage = stage;
        this.resource = resource;
        this.reason = reason;
    }

    public UiException(String stage, String resource, String reason) {
        this(stage, resource, reason, null);
    }

    /** @return the stage that failed, for example {@code "load UI definition"} */
    public String stage() {
        return stage;
    }

    /** @return the UI resource involved, or {@code null} when the failure concerns no single resource */
    public String resource() {
        return resource;
    }

    /** @return the safe reason of the failure */
    public String reason() {
        return reason;
    }

    private static String describe(String stage, String resource, String reason) {
        return "could not " + stage + (resource == null ? "" : " (resource: " + resource + ")") + ": " + reason;
    }
}
