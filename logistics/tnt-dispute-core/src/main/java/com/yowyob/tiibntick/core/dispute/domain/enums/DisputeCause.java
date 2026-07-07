package com.yowyob.tiibntick.core.dispute.domain.enums;

/**
 * Root cause of a dispute.
 *
 * @author MANFOUO Braun
 */
public enum DisputeCause {
    PACKAGE_LOST,
    PACKAGE_DAMAGED,
    DELIVERY_DELAYED,
    WRONG_CONTENT,
    NON_DELIVERY,
    FRAUD,
    HUB_INCIDENT,
    NETWORK_INCIDENT,
    PAYMENT_DISPUTE,
    OTHER
}
