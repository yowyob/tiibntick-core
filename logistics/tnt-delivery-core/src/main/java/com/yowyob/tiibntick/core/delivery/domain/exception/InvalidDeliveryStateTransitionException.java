package com.yowyob.tiibntick.core.delivery.domain.exception;

import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryStatus;

/**
 * Thrown when an illegal state transition is attempted on a {@code Delivery} aggregate.
 *
 * @author MANFOUO Braun
 */
public class InvalidDeliveryStateTransitionException extends DeliveryDomainException {

    private final DeliveryStatus from;
    private final DeliveryStatus to;

    public InvalidDeliveryStateTransitionException(DeliveryStatus from, DeliveryStatus to) {
        super(String.format("Cannot transition delivery from [%s] to [%s]", from, to));
        this.from = from;
        this.to = to;
    }

    public DeliveryStatus getFrom() { return from; }
    public DeliveryStatus getTo()   { return to; }
}
