package com.yowyob.tiibntick.common.exception;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Thrown when input validation fails in TiiBnTick Core.
 * Carries structured field violation details for RFC 7807 Problem Detail responses.
 * Maps to HTTP 422 at the REST adapter layer.
 *
 * Author: MANFOUO Braun
 * Created: 2025-10-01
 */
public class TntValidationException extends TntException {

    private final List<FieldViolation> violations;

    public TntValidationException(String message) {
        super("VALIDATION_ERROR", message);
        this.violations = Collections.emptyList();
    }

    public TntValidationException(String message, List<FieldViolation> violations) {
        super("VALIDATION_ERROR", message);
        this.violations = violations == null ? Collections.emptyList()
            : Collections.unmodifiableList(violations);
    }

    public TntValidationException(String errorCode, String message, List<FieldViolation> violations) {
        super(errorCode, message);
        this.violations = violations == null ? Collections.emptyList()
            : Collections.unmodifiableList(violations);
    }

    /** Returns the list of specific field violations (may be empty). */
    public List<FieldViolation> getViolations() {
        return violations;
    }

    // ─── Inner type ──────────────────────────────────────────────────────

    /**
     * Represents a single field-level validation failure.
     *
     * @param field   the field path that failed validation (e.g., "weightKg", "recipient.phone")
     * @param message human-readable description of the violation
     * @param value   the rejected value (may be null)
     */
    public record FieldViolation(String field, String message, Object value) {

        public FieldViolation {
            Objects.requireNonNull(field, "field must not be null");
            Objects.requireNonNull(message, "message must not be null");
        }

        /** Convenience constructor without the rejected value. */
        public FieldViolation(String field, String message) {
            this(field, message, null);
        }
    }
}
