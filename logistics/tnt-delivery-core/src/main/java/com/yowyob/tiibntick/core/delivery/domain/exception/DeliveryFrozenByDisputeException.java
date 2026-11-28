package com.yowyob.tiibntick.core.delivery.domain.exception;

import java.util.UUID;

/**
 * The parcel of the delivery is under a dispute that freezes its delivery (QO-7: any dispute other than a delay):
 * only a final failure (rows D20, D21) is allowed. Mapped to HTTP 409.
 */
public class DeliveryFrozenByDisputeException extends DeliveryDomainException {

    public DeliveryFrozenByDisputeException(UUID deliveryId, UUID disputeId) {
        super("Delivery " + deliveryId + " is frozen by dispute " + disputeId
                + " (only a final failure is allowed while the dispute is open)");
    }
}
