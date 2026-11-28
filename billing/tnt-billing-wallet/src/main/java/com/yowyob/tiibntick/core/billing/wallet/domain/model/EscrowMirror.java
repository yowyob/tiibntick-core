package com.yowyob.tiibntick.core.billing.wallet.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Local mirror of the Kernel escrow of a delivery (spec §4, decision [H]): the Kernel holds the money, the Core keeps
 * the escrow id and its last known state. One escrow per delivery and purpose ({@link #purpose()}: {@code DELIVERY}
 * for the price, {@code RETURN_GUARANTEE} for D-147/D-151).
 */
public record EscrowMirror(UUID id, UUID tenantId, UUID deliveryId, String purpose, UUID kernelEscrowId,
                           Status status, BigDecimal amount, String currency, UUID payerWalletId, String reason,
                           Instant createdAt, Instant updatedAt) {

    public static final String DELIVERY = "DELIVERY";
    public static final String RETURN_GUARANTEE = "RETURN_GUARANTEE";

    /** Kernel escrow states (spec §4.2, E1–E5). */
    public enum Status {
        HELD, FROZEN, RELEASED, REFUNDED;

        public boolean isFinal() {
            return this == RELEASED || this == REFUNDED;
        }

        /** Maps a Kernel status string; unknown values are kept as the closest known state or {@code null}. */
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

    public EscrowMirror with(Status newStatus, String newReason, Instant at) {
        return new EscrowMirror(id, tenantId, deliveryId, purpose, kernelEscrowId, newStatus, amount, currency,
                payerWalletId, newReason, createdAt, at);
    }
}
