package com.yowyob.tiibntick.core.agency.billing.adapter.out.clients;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.Map;

@Component
public class InvoiceCoreClient implements InvoiceGenerationPort {

    private static final Logger log = LoggerFactory.getLogger(InvoiceCoreClient.class);

    private final WebClient webClient;

    public InvoiceCoreClient(@Qualifier("agencyPlatformWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    @Override
    public Mono<GeneratedInvoice> generate(InvoiceGenerationRequest request) {
        Map<String, Object> body = Map.of(
                "missionId", request.missionId(),
                "clientId", request.clientId(),
                "agencyId", request.agencyId().toString(),
                "amount", request.amount(),
                "currency", request.currency(),
                "description", request.description()
        );
        return webClient.post()
                .uri("/api/v1/billing/invoices/generate")
                .header("X-Tenant-Id", request.tenantId().toString())
                .bodyValue(body)
                .retrieve()
                .bodyToMono(CoreInvoiceResponse.class)
                .map(r -> new GeneratedInvoice(
                        r.id(), r.invoiceNumber(), r.totalAmount(), r.currency(), r.status(), r.pdfUrl()
                ))
                .onErrorResume(e -> {
                    log.warn("[Invoice] generate failed missionId={}: {}", request.missionId(), e.getMessage());
                    return Mono.empty();
                });
    }

    @Override
    public Mono<String> getPdfUrl(String coreInvoiceId) {
        return webClient.get()
                .uri("/api/v1/billing/invoices/{id}/pdf", coreInvoiceId)
                .retrieve()
                .bodyToMono(String.class)
                .onErrorResume(e -> {
                    log.warn("[Invoice] PDF URL unavailable invoiceId={}: {}", coreInvoiceId, e.getMessage());
                    return Mono.empty();
                });
    }

    private record CoreInvoiceResponse(
            String id,
            String invoiceNumber,
            BigDecimal totalAmount,
            String currency,
            String status,
            String pdfUrl
    ) {}
}
