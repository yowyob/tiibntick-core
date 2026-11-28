package com.yowyob.tiibntick.core.delivery.domain.model.enums;

/**
 * Nature of a delivery, fixed at creation (spec §1.1, decision [B.3]): the parcel keeps its history across its
 * deliveries; a {@link #RETURN} or {@link #REDELIVERY} is linked to its origin by {@code parentDeliveryId}.
 */
public enum DeliveryKind {
    /** First delivery of the parcel to its recipient. */
    OUTBOUND,
    /** Return of the parcel to its sender [B.3]. */
    RETURN,
    /** New delivery ordered by a dispute [B.7]. */
    REDELIVERY
}
