package com.yowyob.tiibntick.core.gofreelancer.domain.exception;

/**
 * Exception thrown when a token is expired.
 *
 * @author Kengfack Lagrange
 * @date 19/12/2025
 */
public class ExpiredTokenException extends RuntimeException {
    public ExpiredTokenException(String message) {
        super(message);
    }
}
