package com.yowyob.tiibntick.core.delivery.domain.exception;

import java.util.UUID;

/**
 * Thrown when a delivery cannot be found by its identifier or tracking code.
 *
 * @author MANFOUO Braun
 */
public class DeliveryNotFoundException extends DeliveryDomainException {

    public DeliveryNotFoundException(UUID id) {
        super("Delivery not found for id: " + id);
    }

    public DeliveryNotFoundException(String trackingCode) {
        super("Delivery not found for tracking code: " + trackingCode);
    }
}
