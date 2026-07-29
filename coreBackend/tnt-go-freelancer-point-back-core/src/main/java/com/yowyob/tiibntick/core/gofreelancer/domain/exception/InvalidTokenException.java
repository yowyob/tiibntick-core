package com.yowyob.tiibntick.core.gofreelancer.domain.exception;

/**
 * Exception thrown when a token is invalid.
 *
 * @author Kengfack Lagrange
 * @date 19/12/2025
 */
public class InvalidTokenException extends RuntimeException {
    public InvalidTokenException(String message) {
        super(message);
    }
}
