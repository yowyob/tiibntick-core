package com.yowyob.tiibntick.core.delivery.domain.model.enums;

/**
 * Urgency level of a delivery, affecting pricing and routing priority.
 *
 * @author MANFOUO Braun
 */
public enum DeliveryUrgency {

    /** Standard delivery with no time constraint. */
    STANDARD,

    /** Express delivery expected within a few hours. */
    EXPRESS,

    /** Same-day delivery required. */
    SAME_DAY,

    /** Scheduled delivery for a precise time window. */
    SCHEDULED
}
