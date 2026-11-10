package com.yowyob.tiibntick.core.billing.invoice.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event: emitted when an invoice is cancelled.
 *
 * <p>{@code tenantId} lets payload-only consumers (the report projection) attribute the event; the
 * original three-argument constructor remains.
 *
 * @author MANFOUO Braun
 */
public record InvoiceCancelled(
        UUID invoiceId,
        String reason,
        Instant occurredAt,
        UUID tenantId
) {
    public InvoiceCancelled(UUID invoiceId, String reason, Instant occurredAt) {
        this(invoiceId, reason, occurredAt, null);
    }
}
