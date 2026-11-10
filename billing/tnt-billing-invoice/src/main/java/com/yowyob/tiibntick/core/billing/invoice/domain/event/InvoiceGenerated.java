package com.yowyob.tiibntick.core.billing.invoice.domain.event;

import com.yowyob.tiibntick.core.billing.invoice.domain.model.Money;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event: emitted when an invoice transitions to ISSUED state.
 *
 * <p>The party fields ({@code clientId}, {@code issuerOrgType}, {@code issuerOrgId}, …) are what the
 * report projection needs to attribute the invoice to a client and to the driver/organisation that
 * issued it; they were missing, so per-issuer reports could never find any invoice. They are
 * additive: consumers that ignore them are unaffected, and the original six-argument constructor
 * remains for callers that have no party context.
 *
 * @author MANFOUO Braun
 */
public record InvoiceGenerated(
        UUID invoiceId,
        String invoiceNumber,
        String missionId,
        Money amount,
        UUID tenantId,
        Instant occurredAt,
        String clientId,
        String countryCode,
        String issuerOrgType,
        String issuerOrgId,
        String appliedTemplateName
) {
    public InvoiceGenerated(UUID invoiceId, String invoiceNumber, String missionId, Money amount,
                            UUID tenantId, Instant occurredAt) {
        this(invoiceId, invoiceNumber, missionId, amount, tenantId, occurredAt, null, null, null, null, null);
    }
}
