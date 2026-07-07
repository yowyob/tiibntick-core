package com.yowyob.tiibntick.common.domain.result;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Structured failure representation used by the {@link Result} monad.
 *
 * <p>A failure carries a machine-readable {@code errorCode} (e.g., {@code "MISSION_NOT_FOUND"}),
 * a human-readable {@code message}, an optional list of field-level violations for validation
 * failures, and an optional chained {@code cause} throwable for wrapping infrastructure errors.
 *
 * Author: MANFOUO Braun
 */
public final class TntFailure {

    private final String errorCode;
    private final String message;
    private final List<FieldViolation> violations;
    private final Throwable cause;

    private TntFailure(String errorCode, String message, List<FieldViolation> violations, Throwable cause) {
        this.errorCode  = Objects.requireNonNull(errorCode, "errorCode is required");
        this.message    = Objects.requireNonNull(message, "message is required");
        this.violations = violations == null ? Collections.emptyList() : List.copyOf(violations);
        this.cause      = cause;
    }

    /** Simple error with code and message. */
    public static TntFailure of(String errorCode, String message) {
        return new TntFailure(errorCode, message, null, null);
    }

    /** Validation error with field-level violations. */
    public static TntFailure ofValidation(String errorCode, String message, List<FieldViolation> violations) {
        return new TntFailure(errorCode, message, violations, null);
    }

    /** Infrastructure / unexpected error wrapping a throwable. */
    public static TntFailure ofException(String errorCode, Throwable cause) {
        return new TntFailure(errorCode, cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName(), null, cause);
    }

    public String errorCode()              { return errorCode; }
    public String message()                { return message; }
    public List<FieldViolation> violations(){ return violations; }
    public Throwable cause()               { return cause; }
    public boolean hasViolations()         { return !violations.isEmpty(); }
    public boolean hasCause()              { return cause != null; }

    @Override
    public String toString() {
        return "TntFailure{errorCode='" + errorCode + "', message='" + message + "'}";
    }

    // ── Field-level violation ─────────────────────────────────────────────

    /**
     * Represents a single field-level validation constraint violation.
     *
     * @param field   field path (e.g., "address.city")
     * @param message human-readable constraint message
     */
    public record FieldViolation(String field, String message) {

        public FieldViolation {
            Objects.requireNonNull(field, "field is required");
            Objects.requireNonNull(message, "message is required");
        }
    }
}
