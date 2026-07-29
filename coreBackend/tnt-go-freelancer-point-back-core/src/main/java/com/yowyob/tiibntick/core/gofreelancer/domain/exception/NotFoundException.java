package com.yowyob.tiibntick.core.gofreelancer.domain.exception;

/**
 * Exception thrown when a requested resource is not found.
 *
 * @author Kengfack Lagrange
 * @date 19/12/2025
 */
public class NotFoundException extends RuntimeException {

    /**
     * Constructs a new NotFoundException with the specified message.
     *
     * @param message the detail message
     */
    public NotFoundException(String message) {
        super(message);
    }
}
