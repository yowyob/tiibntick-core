package com.yowyob.tiibntick.core.billing.wallet.domain.model;

import com.yowyob.tiibntick.core.billing.wallet.domain.enums.PaymentChannel;
import com.yowyob.tiibntick.core.billing.wallet.domain.enums.PaymentIntentStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * PaymentIntent — entity that tracks a single in-flight payment request.
 * Created when the user initiates a payment; updated via webhook callback.
 *
 * @author MANFOUO Braun
 */
@Getter
@Builder
public class PaymentIntent {

    private final PaymentIntentId id;
    private final WalletId walletId;
    /** Link to the invoice being paid (nullable for top-up flows). */
    private final String invoiceId;
    private final Money amount;
    private final PaymentChannel channel;
    private PaymentIntentStatus status;
    /** External provider reference — set after provider confirmation. */
    private String externalRef;
    private final String idempotencyKey;
    /** Redirect URL used by redirect-based flows (Orange Money). */
    private final String callbackUrl;
    private final LocalDateTime expiresAt;
    @Builder.Default
    private final Map<String, String> metadata = new HashMap<>();
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * Confirms this intent with the external provider reference.
     *
     * @param providerRef financial transaction ID returned by the provider
     */
    public void confirm(String providerRef) {
        if (this.status != PaymentIntentStatus.PENDING) {
            throw new IllegalStateException("Cannot confirm intent in status: " + this.status);
        }
        this.externalRef = providerRef;
        this.status = PaymentIntentStatus.CONFIRMED;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Marks this intent as failed.
     *
     * @param reason human-readable failure description from the provider
     */
    public void fail(String reason) {
        if (this.status != PaymentIntentStatus.PENDING) {
            throw new IllegalStateException("Cannot fail intent in status: " + this.status);
        }
        this.status = PaymentIntentStatus.FAILED;
        this.metadata.put("failureReason", reason);
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Expires the intent when the TTL has elapsed without confirmation.
     */
    public void expire() {
        if (this.status == PaymentIntentStatus.PENDING) {
            this.status = PaymentIntentStatus.EXPIRED;
            this.updatedAt = LocalDateTime.now();
        }
    }

    /**
     * Cancels the intent explicitly.
     */
    public void cancel() {
        if (this.status == PaymentIntentStatus.CONFIRMED || this.status == PaymentIntentStatus.REFUNDED) {
            throw new IllegalStateException("Cannot cancel intent in status: " + this.status);
        }
        this.status = PaymentIntentStatus.CANCELLED;
        this.updatedAt = LocalDateTime.now();
    }

    public boolean isPending() {
        return PaymentIntentStatus.PENDING == this.status;
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt) || this.status == PaymentIntentStatus.EXPIRED;
    }
}
