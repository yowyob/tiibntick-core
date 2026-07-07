package com.yowyob.tiibntick.common.api;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Structured error detail embedded in {@link ApiResponse} when {@code status == ERROR}.
 *
 * <p>Provides a machine-readable {@code code} for client-side error handling,
 * a human-readable {@code message} for display, and optional field-level
 * {@code violations} for form validation feedback.
 *
 * Author: MANFOUO Braun
 */
public final class ErrorDetail {

    private final String code;
    private final String message;
    private final List<FieldError> violations;
    private final String path;

    private ErrorDetail(String code, String message, List<FieldError> violations, String path) {
        this.code       = Objects.requireNonNull(code, "error code is required");
        this.message    = Objects.requireNonNull(message, "error message is required");
        this.violations = violations == null ? Collections.emptyList() : List.copyOf(violations);
        this.path       = path;
    }

    /** Creates a simple error with code and message. */
    public static ErrorDetail of(String code, String message) {
        return new ErrorDetail(code, message, null, null);
    }

    /** Creates a validation error with field-level violations. */
    public static ErrorDetail ofValidation(String code, String message, List<FieldError> violations) {
        return new ErrorDetail(code, message, violations, null);
    }

    /** Creates an error with the request path for debugging. */
    public static ErrorDetail of(String code, String message, String path) {
        return new ErrorDetail(code, message, null, path);
    }

    public String getCode()              { return code; }
    public String getMessage()           { return message; }
    public List<FieldError> getViolations(){ return violations; }
    public String getPath()              { return path; }
    public boolean hasViolations()       { return !violations.isEmpty(); }

    @Override
    public String toString() {
        return "ErrorDetail{code='" + code + "', message='" + message + "'}";
    }

    // ── Field-level error ─────────────────────────────────────────────────

    /**
     * Single field-level error for validation responses.
     *
     * @param field    dot-notation field path (e.g., "recipient.phoneNumber")
     * @param message  constraint violation message
     * @param rejected rejected value for client display (optional, may be omitted for security)
     */
    public record FieldError(String field, String message, Object rejected) {

        public FieldError(String field, String message) {
            this(field, message, null);
        }
    }
}
