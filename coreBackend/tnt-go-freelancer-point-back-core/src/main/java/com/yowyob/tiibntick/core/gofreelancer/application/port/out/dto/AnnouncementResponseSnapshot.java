package com.yowyob.tiibntick.core.gofreelancer.application.port.out.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Gofp-owned read model for a delivery person's bid on an announcement.
 *
 * <p>Returned by {@link com.yowyob.tiibntick.core.gofreelancer.application.port.out.IDeliveryAnnouncementPort}
 * so that gofp's use cases never need to reference {@code tnt-delivery-core}'s own
 * {@code AnnouncementResponse} entity type.
 *
 * @author MANFOUO BRAUN
 */
public record AnnouncementResponseSnapshot(
        UUID id,
        UUID deliveryPersonId,
        BigDecimal proposedPrice,
        String proposedCurrency,
        String status,
        Instant createdAt) {
}
