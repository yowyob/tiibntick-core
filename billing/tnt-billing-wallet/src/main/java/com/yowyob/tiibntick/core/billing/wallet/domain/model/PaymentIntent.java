package com.yowyob.tiibntick.core.billing.wallet.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Payment intent of a selection (spec §4.1): the delivery id chosen before the delivery exists, the split, the payer
 * and the courier; consumed by the hold (ESCROW) or the cash collection (CASH). An expired or consumed intent is
 * refused (409).
 */
public record PaymentIntent(UUID id, UUID tenantId, UUID deliveryId, UUID announcementId, UUID responseId,
                            Mode mode, BigDecimal amount, String currency, EscrowShares shares, UUID payerActorId,
                            UUID payerWalletId, UUID courierActorId, UUID courierWalletId, UUID platformWalletId,
                            Instant expiresAt, Instant consumedAt, Instant createdAt) {

    /**
     * ESCROW: held from the payer's wallet with his code; CASH: collected by the courier; FUND: a return at the
     * courier's or the platform's expense, paid to the return's courier by the compensation fund at delivery
     * (QO-22 / D-115, spec §4.4) — nothing is taken from the sender.
     */
    public enum Mode { ESCROW, CASH, FUND }

    public PaymentIntent {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(deliveryId, "deliveryId");
        Objects.requireNonNull(mode, "mode");
    }

    public boolean expired(Instant now) {
        return expiresAt != null && now.isAfter(expiresAt);
    }

    public PaymentIntent consume(Instant at) {
        return new PaymentIntent(id, tenantId, deliveryId, announcementId, responseId, mode, amount, currency, shares,
                payerActorId, payerWalletId, courierActorId, courierWalletId, platformWalletId, expiresAt, at,
                createdAt);
    }
}
