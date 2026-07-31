package com.yowyob.tiibntick.core.delivery.domain.model.enums;

/**
 * Pricing mode for a client delivery announcement.
 *
 * <ul>
 *   <li>{@link #FIXED_PRICE} — client sets {@code offeredAmount} at publish; escrow at publish.</li>
 *   <li>{@link #QUOTE_REQUEST} — freelancers propose a price on respond; escrow at select.</li>
 * </ul>
 *
 * @author MANFOUO BRAUN
 */
public enum AnnouncementPricingMode {
    FIXED_PRICE,
    QUOTE_REQUEST
}
