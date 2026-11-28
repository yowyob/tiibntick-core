package com.yowyob.tiibntick.core.billing.wallet.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A change of courier on a delivery (QO-11 / D-153), as published in the {@code details} of the delivery event that
 * resumed it: the courier who left, the replacement, the leg of the one who left and what remained to the delivery
 * address (straight-line kilometres, {@code null} when a point was unknown). One row per delivery event.
 *
 * @param aggregateVersion version of the delivery after the change — orders the changes of one delivery
 */
public record CourierLeg(UUID id, UUID tenantId, UUID deliveryId, UUID eventId, long aggregateVersion,
                         UUID replacedCourierId, UUID replacementCourierId, BigDecimal replacedLegKm,
                         BigDecimal remainingKm, Instant createdAt) {
}
