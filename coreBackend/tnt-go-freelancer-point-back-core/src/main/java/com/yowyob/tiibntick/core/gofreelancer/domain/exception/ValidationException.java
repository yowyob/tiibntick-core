package com.yowyob.tiibntick.core.gofreelancer.domain.exception;

/**
 * Exception thrown when validation fails.
 *
 * @author Kengfack Lagrange
 * @date 19/12/2025
 */
public class ValidationException extends RuntimeException {
    public ValidationException(String message) {
        super(message);
    }
}
