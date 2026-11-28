package com.yowyob.tiibntick.core.billing.wallet.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Local mirror of the Kernel cash collection of a delivery paid in cash (spec §4.1 mode CASH, §4.2 K1–K5): the courier
 * collects the price and remits what the platform is owed; the outstanding amounts are the courier's debt.
 */
public record CashCollectionMirror(UUID id, UUID tenantId, UUID deliveryId, UUID kernelCollectionId,
                                   UUID courierActorId, UUID courierWalletId, BigDecimal expectedAmount,
                                   BigDecimal collectedAmount, BigDecimal amountToRemit, String currency,
                                   Status status, Instant createdAt, Instant updatedAt) {

    public enum Status {
        OPEN, COLLECTED, REMITTED, CANCELLED;

        public static Status of(String kernel) {
            if (kernel == null) {
                return null;
            }
            try {
                return valueOf(kernel.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    public CashCollectionMirror with(Status newStatus, BigDecimal collected, BigDecimal toRemit, Instant at) {
        return new CashCollectionMirror(id, tenantId, deliveryId, kernelCollectionId, courierActorId, courierWalletId,
                expectedAmount, collected != null ? collected : collectedAmount,
                toRemit != null ? toRemit : amountToRemit, currency, newStatus, createdAt, at);
    }
}
