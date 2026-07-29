package com.yowyob.tiibntick.core.gofreelancer.domain.exception;

/**
 * Exception thrown when an email is already used.
 *
 * @author Kengfack Lagrange
 * @date 19/12/2025
 */
public class EmailAlreadyUsedException extends RuntimeException {
    public EmailAlreadyUsedException(String message) {
        super(message);
    }
}
