package com.yowyob.tiibntick.core.billing.invoice.domain.event;

import com.yowyob.tiibntick.core.billing.invoice.domain.model.Money;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event: emitted when an invoice transitions to ISSUED state.
 *
 * @author MANFOUO Braun
 */
public record InvoiceGenerated(
        UUID invoiceId,
        String invoiceNumber,
        String missionId,
        Money amount,
        UUID tenantId,
        Instant occurredAt
) {}
