package com.yowyob.tiibntick.core.delivery.domain.exception;

/**
 * A pair of states (or a guard) refused by a transition table of delivery-core — parcel or announcement (the delivery
 * keeps its own {@link InvalidDeliveryStateTransitionException}). Mapped to HTTP 409.
 */
public class InvalidStateTransitionException extends DeliveryDomainException {

    private final String aggregate;
    private final String from;
    private final String to;

    public InvalidStateTransitionException(String aggregate, Object from, Object to, String reason) {
        super(String.format("Cannot transition %s from [%s] to [%s]%s", aggregate, from, to,
                reason == null ? "" : ": " + reason));
        this.aggregate = aggregate;
        this.from = String.valueOf(from);
        this.to = String.valueOf(to);
    }

    public String getAggregate() { return aggregate; }
    public String getFrom() { return from; }
    public String getTo() { return to; }
}
