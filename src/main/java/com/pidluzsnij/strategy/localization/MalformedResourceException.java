package com.pidluzsnij.strategy.localization;

/** A resource whose content violates its format; the message never quotes resource content. */
final class MalformedResourceException extends Exception {

    MalformedResourceException(int line, String problem) {
        super("line " + line + ": " + problem);
    }

    MalformedResourceException(String problem) {
        super(problem);
    }
}
