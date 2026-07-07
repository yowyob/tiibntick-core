package com.yowyob.tiibntick.core.billing.wallet.domain.model;

import com.yowyob.tiibntick.core.billing.wallet.domain.enums.PaymentChannel;
import com.yowyob.tiibntick.core.billing.wallet.domain.enums.TransactionStatus;
import com.yowyob.tiibntick.core.billing.wallet.domain.enums.TransactionType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * WalletTransaction — entity owned by the Wallet aggregate.
 * Represents a single financial movement (credit, debit, reserve, refund).
 *
 * @author MANFOUO Braun
 */
@Getter
@Builder
public class WalletTransaction {

    private final TransactionId id;
    private final WalletId walletId;
    private final TransactionType type;
    private final Money amount;
    private Money balanceAfter;
    private final PaymentChannel channel;
    /** Internal reference — e.g. invoiceId or missionId. */
    private final String referenceId;
    /** External provider reference — e.g. MTN referenceId, Stripe paymentIntentId. */
    private String externalRef;
    private TransactionStatus status;
    private final String description;
    /**
     * Idempotency key — format: {invoiceId}:{channel} (used by MoMo payments).
     * Stored for audit even after the Redis lock is released.
     */
    private final String idempotencyKey;
    private String failureReason;
    private final LocalDateTime createdAt;
    private LocalDateTime processedAt;

    /**
     * Marks this transaction as confirmed by the external provider.
     * Updates status, externalRef and processedAt.
     *
     * @param providerRef external financial transaction id from the provider
     */
    public void confirm(String providerRef) {
        if (this.status != TransactionStatus.PENDING) {
            throw new IllegalStateException(
                    "Cannot confirm transaction in status: " + this.status);
        }
        this.externalRef = Objects.requireNonNull(providerRef, "providerRef must not be null");
        this.status = TransactionStatus.CONFIRMED;
        this.processedAt = LocalDateTime.now();
    }

    /**
     * Marks this transaction as failed.
     *
     * @param reason human-readable failure reason from the provider
     */
    public void fail(String reason) {
        if (this.status != TransactionStatus.PENDING) {
            throw new IllegalStateException(
                    "Cannot fail transaction in status: " + this.status);
        }
        this.failureReason = reason;
        this.status = TransactionStatus.FAILED;
        this.processedAt = LocalDateTime.now();
    }

    /**
     * Creates a REFUND counter-transaction from this confirmed transaction.
     *
     * @return new REFUND transaction (caller must add it to the wallet)
     */
    public WalletTransaction refund() {
        if (this.status != TransactionStatus.CONFIRMED) {
            throw new IllegalStateException("Can only refund CONFIRMED transactions");
        }
        this.status = TransactionStatus.REFUNDED;
        return WalletTransaction.builder()
                .id(TransactionId.generate())
                .walletId(this.walletId)
                .type(TransactionType.REFUND)
                .amount(this.amount)
                .channel(this.channel)
                .referenceId("REFUND-" + this.id.value())
                .externalRef(null)
                .status(TransactionStatus.CONFIRMED)
                .description("Refund for transaction " + this.id.value())
                .idempotencyKey("REFUND-" + this.idempotencyKey)
                .createdAt(LocalDateTime.now())
                .processedAt(LocalDateTime.now())
                .build();
    }

    public boolean isPending() {
        return TransactionStatus.PENDING == this.status;
    }

    public boolean isConfirmed() {
        return TransactionStatus.CONFIRMED == this.status;
    }

    public boolean isFailed() {
        return TransactionStatus.FAILED == this.status;
    }

    /**
     * Updates the balance-after field when a pending debit is confirmed.
     * Called by the Wallet aggregate after adjusting its balance.
     */
    public void setBalanceAfterConfirmation(Money balance) {
        this.balanceAfter = balance;
    }
}
