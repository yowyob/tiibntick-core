package com.yowyob.tiibntick.core.billing.wallet.domain.exception;

/**
 * Thrown when an idempotency check detects a duplicate payment request.
 * Callers should return the original response rather than processing again.
 * @author MANFOUO Braun
 */
public class DuplicatePaymentException extends RuntimeException {
    private final String idempotencyKey;

    public DuplicatePaymentException(String idempotencyKey) {
        super("Duplicate payment detected for idempotency key: " + idempotencyKey);
        this.idempotencyKey = idempotencyKey;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }
}
