package com.yowyob.tiibntick.core.gofreelancer.domain.exception;

/**
 * Exception thrown when attempting to create a resource that already exists.
 *
 * @author Kenmeugne Michèle
 * @date 18/12/2025
 */
public class DuplicateResourceException extends RuntimeException {

    /**
     * Constructs a new DuplicateResourceException with the specified message.
     *
     * @param message error message
     * @author Kenmeugne Michèle
     * @date 18/12/2025
     */
    public DuplicateResourceException(String message) {
        super(message);
    }

    /**
     * Constructs a new DuplicateResourceException for a specific field.
     *
     * @param resourceName name of the resource
     * @param fieldName name of the duplicate field
     * @param fieldValue value of the duplicate field
     * @author Kengfack Lagrange
     * @date 18/12/2025
     */
    public DuplicateResourceException(String resourceName, String fieldName, Object fieldValue) {
        super(String.format("%s already exists with %s: '%s'", resourceName, fieldName, fieldValue));
    }
}
