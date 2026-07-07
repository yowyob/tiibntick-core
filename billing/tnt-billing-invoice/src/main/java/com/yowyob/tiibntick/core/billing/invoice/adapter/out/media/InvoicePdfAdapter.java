package com.yowyob.tiibntick.core.billing.invoice.adapter.out.media;

import com.yowyob.tiibntick.core.billing.invoice.application.port.out.InvoicePdfPort;
import com.yowyob.tiibntick.core.billing.invoice.domain.model.Invoice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Adapter: implements InvoicePdfPort by calling tnt-media-core REST API.
 * Delegates PDF generation and MinIO storage to the dedicated media module.
 *
 * @author MANFOUO Braun
 */
@Component
public class InvoicePdfAdapter implements InvoicePdfPort {

    private static final Logger log = LoggerFactory.getLogger(InvoicePdfAdapter.class);

    private final WebClient mediaWebClient;

    public InvoicePdfAdapter(
            @Value("${tnt.media-core.base-url:http://localhost:8080}") String mediaBaseUrl) {
        this.mediaWebClient = WebClient.builder()
                .baseUrl(mediaBaseUrl)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @Override
    public Mono<String> generateAndStore(Invoice invoice) {
        Map<String, Object> request = Map.of(
                "invoiceId",     invoice.getId().toString(),
                "invoiceNumber", invoice.getNumber().value(),
                "tenantId",      invoice.getTenantId().toString(),
                "clientId",      invoice.getClientId(),
                "netAmount",     invoice.getNetAmount().amount(),
                "currency",      invoice.getNetAmount().currency(),
                "issuedAt",      invoice.getIssuedAt() != null ? invoice.getIssuedAt().toString() : "",
                "dueAt",         invoice.getDueAt() != null ? invoice.getDueAt().toString() : ""
        );

        return mediaWebClient.post()
                .uri("/api/v1/media/pdf/invoice")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> (String) response.get("storageKey"))
                .doOnSuccess(key -> log.info("PDF generated for invoice {}: key={}", invoice.getNumber(), key))
                .onErrorResume(e -> {
                    log.warn("PDF generation unavailable for invoice {}: {}", invoice.getNumber(), e.getMessage());
                    return Mono.just("invoices/pending/" + invoice.getId() + ".pdf");
                });
    }

    @Override
    public Mono<String> getDownloadUrl(String storageKey, int expirySeconds) {
        return mediaWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/media/pdf/download-url")
                        .queryParam("storageKey", storageKey)
                        .queryParam("expirySeconds", expirySeconds)
                        .build())
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> (String) response.get("url"))
                .onErrorResume(e -> {
                    log.warn("Could not get pre-signed URL for {}: {}", storageKey, e.getMessage());
                    return Mono.just("/api/v1/media/pdf/fallback?key=" + storageKey);
                });
    }
}
