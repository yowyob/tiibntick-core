package com.yowyob.tiibntick.core.billing.dsl.domain.model;

import lombok.Value;

/**
 * Represents a single validation error returned by the DSL validator.
 *
 * @author MANFOUO Braun
 */
@Value
public class ValidationError {

    /** Zero-based position in the DSL expression where the error was detected. */
    int position;

    /** Human-readable message describing the error. */
    String message;

    /** The token text that triggered the error (may be empty). */
    String token;

    public static ValidationError of(int position, String message, String token) {
        return new ValidationError(position, message, token);
    }

    public static ValidationError of(String message) {
        return new ValidationError(-1, message, "");
    }
}
