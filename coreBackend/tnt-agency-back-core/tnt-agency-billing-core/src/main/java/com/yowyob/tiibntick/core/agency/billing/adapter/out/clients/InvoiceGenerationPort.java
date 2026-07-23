package com.yowyob.tiibntick.core.agency.billing.adapter.out.clients;

import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.UUID;

public interface InvoiceGenerationPort {

    Mono<GeneratedInvoice> generate(InvoiceGenerationRequest request);

    Mono<String> getPdfUrl(UUID tenantId, String coreInvoiceId);

    record InvoiceGenerationRequest(
            UUID tenantId,
            UUID agencyId,
            String missionId,
            String clientId,
            BigDecimal amount,
            String currency,
            String description
    ) {}

    record GeneratedInvoice(
            String coreInvoiceId,
            String invoiceNumber,
            BigDecimal amount,
            String currency,
            String status,
            String pdfUrl
    ) {}
}
