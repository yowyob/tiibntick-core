package com.yowyob.tiibntick.core.gofreelancer.application.port.out.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Gofp-owned command for a freelancer's response/bid on an announcement, via
 * {@link com.yowyob.tiibntick.core.gofreelancer.application.port.out.IDeliveryAnnouncementPort}.
 *
 * @author MANFOUO BRAUN
 */
public record RespondToAnnouncementPortCommand(
        UUID tenantId,
        UUID announcementId,
        UUID freelancerId,
        Instant estimatedArrivalTime,
        String note,
        BigDecimal proposedPrice,
        String proposedCurrency) {
}
