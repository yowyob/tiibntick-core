package com.yowyob.tiibntick.core.billing.invoice.application.port.in.command;

import java.util.UUID;

/**
 * Command to cancel an invoice.
 *
 * @author MANFOUO Braun
 */
public record CancelInvoiceCommand(
        UUID tenantId,
        UUID invoiceId,
        String reason
) {}
