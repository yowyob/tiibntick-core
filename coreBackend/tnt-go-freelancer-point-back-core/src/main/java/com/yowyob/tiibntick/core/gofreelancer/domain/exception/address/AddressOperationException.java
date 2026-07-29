package com.yowyob.tiibntick.core.gofreelancer.domain.exception.address;

import java.util.UUID;

/**
 * Exception thrown when an address operation fails.
 *
 * @author Kengfack Lagrange
 * @date 18/12/2025
 */
public class AddressOperationException extends RuntimeException {

    /**
     * Constructs a new AddressOperationException with the specified message.
     */
    public AddressOperationException(String message) {
        super(message);
    }

    /**
     * Constructs a new AddressOperationException with the specified message and cause.
     */
    public AddressOperationException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a new AddressOperationException for a specific operation.
     */
    public AddressOperationException(String operation, UUID addressId, String reason) {
        super(String.format("Failed to %s address with id '%s': %s", operation, addressId, reason));
    }
}