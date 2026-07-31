package com.yowyob.tiibntick.core.gofreelancer.application.port.out.dto;

import com.yowyob.tiibntick.common.vo.Address;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.announcement.AnnouncementStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Gofp-owned read model of a {@code tnt-delivery-core} announcement.
 *
 * <p>This is the only announcement shape {@code MatchingUseCase}/{@code AnnouncementApplicationService}
 * see — {@link com.yowyob.tiibntick.core.gofreelancer.adapter.out.delivery.DeliveryAnnouncementPortAdapter}
 * is the single place that translates {@code tnt-delivery-core}'s {@code DeliveryAnnouncement}
 * aggregate into this record, so a change to that aggregate's internal shape only ever
 * requires touching the adapter, never gofp's business logic.
 *
 * <p>{@code pricingMode} is a plain string mirroring {@code tnt-delivery-core}'s
 * {@code AnnouncementPricingMode} enum name ({@code "FIXED_PRICE"} / {@code "QUOTE_REQUEST"}) —
 * kept as a string, not an enum, so this record has zero compile-time dependency on that module.
 *
 * @author MANFOUO BRAUN
 */
public record AnnouncementSnapshot(
        UUID id,
        UUID tenantId,
        UUID clientId,
        String title,
        String description,
        AnnouncementStatus status,
        Instant createdAt,
        Instant updatedAt,
        BigDecimal offeredAmount,
        String currency,
        String pricingMode,
        Address pickupAddress,
        Address deliveryAddress,
        double packetVolumetricWeightDm3,
        Double packetWeightKg,
        Double packetWidthCm,
        Double packetHeightCm,
        Double packetLengthCm,
        Boolean packetFragile,
        Boolean packetPerishable,
        String packetDescription,
        String packetPhotoUrl,
        String recipientName,
        String recipientPhone,
        UUID selectedResponseId,
        UUID createdDeliveryId,
        String trackingCode,
        List<AnnouncementResponseSnapshot> responses) {

    public static final String PRICING_MODE_QUOTE_REQUEST = "QUOTE_REQUEST";
    public static final String PRICING_MODE_FIXED_PRICE = "FIXED_PRICE";

    /**
     * Returns a copy with delivery linkage fields filled after selection.
     */
    public AnnouncementSnapshot withDeliveryLinkage(UUID deliveryId, String tracking) {
        return new AnnouncementSnapshot(
                id, tenantId, clientId, title, description, status, createdAt, updatedAt,
                offeredAmount, currency, pricingMode, pickupAddress, deliveryAddress,
                packetVolumetricWeightDm3, packetWeightKg, packetWidthCm, packetHeightCm,
                packetLengthCm, packetFragile, packetPerishable, packetDescription, packetPhotoUrl,
                recipientName, recipientPhone, selectedResponseId,
                deliveryId != null ? deliveryId : createdDeliveryId,
                tracking != null ? tracking : trackingCode,
                responses);
    }
}
