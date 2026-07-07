package com.yowyob.tiibntick.core.billing.dsl.domain.exception;

import com.yowyob.tiibntick.core.billing.dsl.domain.model.DslAccessLevel;

/**
 * Exception thrown when a DSL authoring operation is attempted by an actor
 * whose {@link DslAccessLevel} is {@link DslAccessLevel#NONE}, or when a
 * {@link DslAccessLevel#SIMPLIFIED} actor uses a variable or operator that
 * is restricted to {@link DslAccessLevel#FULL} access.
 *
 * @author MANFOUO Braun
 */
public class UnsupportedDslAccessException extends RuntimeException {

    private final DslAccessLevel accessLevel;
    private final String restrictedElement;

    public UnsupportedDslAccessException(DslAccessLevel accessLevel, String restrictedElement) {
        super("DSL element '" + restrictedElement + "' is not allowed "
                + "for access level " + accessLevel + ". "
                + "Upgrade to FULL access or use a supported variable/operator.");
        this.accessLevel = accessLevel;
        this.restrictedElement = restrictedElement;
    }

    public DslAccessLevel getAccessLevel() { return accessLevel; }
    public String getRestrictedElement() { return restrictedElement; }
}
