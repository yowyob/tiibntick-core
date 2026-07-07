package com.yowyob.tiibntick.core.billing.invoice.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event: emitted when an invoice is cancelled.
 *
 * @author MANFOUO Braun
 */
public record InvoiceCancelled(
        UUID invoiceId,
        String reason,
        Instant occurredAt
) {}
