package com.yowyob.tiibntick.core.billing.templates.domain.exception;

import java.util.List;

/**
 * Thrown when one or more template parameter values provided by an actor fail
 * validation (unknown key, value out of [min, max] range, wrong type).
 *
 * <p>The exception carries all validation error messages so the UI can display
 * them all at once rather than requiring multiple round-trips.
 *
 * @author MANFOUO Braun
 * @version 1.0
 * @since 2026-05-26
 */
public class TemplateParameterValidationException extends RuntimeException {

    /** Individual validation error messages. */
    private final List<String> validationErrors;

    public TemplateParameterValidationException(String templateCode, List<String> errors) {
        super("Template '" + templateCode + "' parameter validation failed: " + errors);
        this.validationErrors = List.copyOf(errors);
    }

    /**
     * Returns the list of validation error messages.
     *
     * @return immutable list of error messages
     */
    public List<String> getValidationErrors() {
        return validationErrors;
    }
}
