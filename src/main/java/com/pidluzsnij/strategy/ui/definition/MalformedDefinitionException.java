package com.pidluzsnij.strategy.ui.definition;

/** A UI definition violates the definition syntax or schema. The message never quotes definition content. */
final class MalformedDefinitionException extends Exception {

    MalformedDefinitionException(String message) {
        super(message, null, false, false);
    }
}
