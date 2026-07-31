package com.yowyob.tiibntick.core.gofreelancer.application.port.out.dto;

import com.yowyob.tiibntick.common.vo.Address;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Gofp-owned command to publish a new announcement through
 * {@link com.yowyob.tiibntick.core.gofreelancer.application.port.out.IDeliveryAnnouncementPort}.
 *
 * <p>{@code pricingMode} follows the same string convention as {@link AnnouncementSnapshot}
 * ({@code "FIXED_PRICE"} / {@code "QUOTE_REQUEST"}, case-insensitive, defaults to
 * {@code FIXED_PRICE} when blank — the adapter is responsible for parsing it).
 *
 * @author MANFOUO BRAUN
 */
public record PublishAnnouncementPortCommand(
        UUID tenantId,
        UUID clientId,
        String title,
        String description,
        BigDecimal offeredAmount,
        String currency,
        String pricingMode,
        double packageWeightKg,
        double packageWidthCm,
        double packageHeightCm,
        double packageLengthCm,
        boolean fragile,
        boolean perishable,
        String packageDescription,
        Address pickupAddress,
        Address deliveryAddress,
        String recipientName,
        String recipientPhone) {
}
