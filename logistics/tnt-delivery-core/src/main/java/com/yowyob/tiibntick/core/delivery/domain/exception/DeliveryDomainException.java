package com.yowyob.tiibntick.core.delivery.domain.exception;

/**
 * Root unchecked exception for violations of delivery domain invariants.
 *
 * @author MANFOUO Braun
 */
public class DeliveryDomainException extends RuntimeException {

    public DeliveryDomainException(String message) {
        super(message);
    }

    public DeliveryDomainException(String message, Throwable cause) {
        super(message, cause);
    }
}
