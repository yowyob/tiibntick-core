package com.yowyob.tiibntick.core.gofreelancer.domain.exception;

/**
 * Exception thrown when a requested resource is not found.
 *
 * @author Kenmeugne Michele
 * @date 18/12/2025
 */
public class ResourceNotFoundException extends RuntimeException {

    /**
     * Constructs a new ResourceNotFoundException with the specified message.
     *
     * @param message error message
     * @author Kengfack Lagrange
     * @date 18/12/2025
     */
    public ResourceNotFoundException(String message) {
        super(message);
    }

    /**
     * Constructs a new ResourceNotFoundException for a specific resource type and ID.
     *
     * @param resourceName name of the resource
     * @param fieldName name of the field used for search
     * @param fieldValue value of the field
     * @author Kengfack Lagrange
     * @date 18/12/2025
     */
    public ResourceNotFoundException(String resourceName, String fieldName, Object fieldValue) {
        super(String.format("%s not found with %s: '%s'", resourceName, fieldName, fieldValue));
    }
}
