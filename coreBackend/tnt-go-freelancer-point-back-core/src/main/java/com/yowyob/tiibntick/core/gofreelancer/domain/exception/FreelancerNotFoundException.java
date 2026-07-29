package com.yowyob.tiibntick.core.gofreelancer.domain.exception;

/**
 * Exception thrown when a delivery person is not found.
 *
 * @author Kengfack Lagrange
 * @date 19/12/2025
 */
public class FreelancerNotFoundException extends RuntimeException {
    public FreelancerNotFoundException(String message) {
        super(message);
    }
}
