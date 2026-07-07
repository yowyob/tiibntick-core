package com.yowyob.tiibntick.core.billing.invoice.domain.event;

import com.yowyob.tiibntick.core.billing.invoice.domain.model.Money;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event: emitted when an invoice is marked as PAID.
 *
 * @author MANFOUO Braun
 */
public record InvoicePaid(
        UUID invoiceId,
        String paymentRef,
        Money amount,
        Instant occurredAt
) {}
