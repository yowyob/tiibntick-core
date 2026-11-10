package com.yowyob.tiibntick.core.billing.invoice.domain.event;

import com.yowyob.tiibntick.core.billing.invoice.domain.model.Money;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event: emitted when an invoice is marked as PAID.
 *
 * <p>{@code tenantId} lets consumers that read only the payload (the report projection drops events
 * without it) attribute the payment; the original four-argument constructor remains.
 *
 * @author MANFOUO Braun
 */
public record InvoicePaid(
        UUID invoiceId,
        String paymentRef,
        Money amount,
        Instant occurredAt,
        UUID tenantId
) {
    public InvoicePaid(UUID invoiceId, String paymentRef, Money amount, Instant occurredAt) {
        this(invoiceId, paymentRef, amount, occurredAt, null);
    }
}
