package com.yowyob.tiibntick.core.delivery.domain.exception;

import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryStatus;

/**
 * Thrown when a pair of delivery states, or a guard of a row, is refused by
 * {@code DeliveryStateMachine.TABLE} (spec §1.1: "toute paire absente de cette table est refusée", HTTP 409).
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

    public InvalidDeliveryStateTransitionException(DeliveryStatus from, DeliveryStatus to, String guard) {
        super(String.format("Cannot transition delivery from [%s] to [%s]: %s", from, to, guard));
        this.from = from;
        this.to = to;
    }

    public DeliveryStatus getFrom() { return from; }
    public DeliveryStatus getTo()   { return to; }
}
