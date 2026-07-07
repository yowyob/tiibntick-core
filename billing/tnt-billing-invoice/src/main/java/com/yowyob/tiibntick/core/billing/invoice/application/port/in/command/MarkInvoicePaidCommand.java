package com.yowyob.tiibntick.core.billing.invoice.application.port.in.command;

import java.util.UUID;

/**
 * Command to mark an invoice as paid after receiving a payment confirmation.
 *
 * @author MANFOUO Braun
 */
public record MarkInvoicePaidCommand(
        UUID tenantId,
        UUID invoiceId,
        String paymentRef
) {}
