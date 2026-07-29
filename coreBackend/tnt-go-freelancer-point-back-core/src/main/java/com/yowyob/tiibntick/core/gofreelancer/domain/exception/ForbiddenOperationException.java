package com.yowyob.tiibntick.core.gofreelancer.domain.exception;

/**
 * Exception thrown when an operation is forbidden.
 *
 * @author Kengfack Lagrange
 * @date 19/12/2025
 */
public class ForbiddenOperationException extends RuntimeException {
    public ForbiddenOperationException(String message) {
        super(message);
    }
}
