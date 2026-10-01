package com.pidluzsnij.strategy.config;

import java.util.Objects;

/**
 * A recognized setting whose persisted value was replaced with its default.
 * Carries no persisted value so that it can be reported safely.
 *
 * @param id           the setting's identifier
 * @param reason       why the persisted value was rejected
 * @param expectedType name of the setting's required type
 */
public record InvalidSetting(String id, Reason reason, String expectedType) {

    public enum Reason {
        /** The persisted value cannot be converted to the setting's type. */
        INCOMPATIBLE_TYPE,
        /** The persisted value has the required type but violates the setting's validation rule. */
        FAILED_VALIDATION,
        /** A section enclosing the setting is persisted as something other than a table. */
        SECTION_NOT_A_TABLE
    }

    public InvalidSetting {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(expectedType, "expectedType");
    }
}
