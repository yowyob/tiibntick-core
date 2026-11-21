package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.response;

/**
 * Lot C-53 — the handover codes of a delivery need, for its owner only. {@code pickupCode} is
 * {@code null} once the parcel was picked up; {@code deliveryCode} is {@code null} once the delivery
 * is delivered, cancelled or failed.
 */
public record HandoverCodesDTO(String pickupCode, String deliveryCode, String deliveryStatus) { }
