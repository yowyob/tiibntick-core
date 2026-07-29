package com.yowyob.tiibntick.core.gofreelancer.domain.exception.address;

/**
 * Exception thrown when address data is invalid.
 *
 * @author Kengfack Lagrange
 * @date 18/12/2025
 */
public class InvalidAddressException extends RuntimeException {

    /**
     * Constructs a new InvalidAddressException with the specified message.
     */
    public InvalidAddressException(String message) {
        super(message);
    }

    /**
     * Constructs a new InvalidAddressException for a specific field.
     */
    public InvalidAddressException(String fieldName, String reason) {
        super(String.format("Invalid address data for field '%s': %s", fieldName, reason));
    }
}