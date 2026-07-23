package com.yowyob.tiibntick.core.agency.billing.adapter.out.clients;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class InvoiceCoreClient implements InvoiceGenerationPort {

    private static final Logger log = LoggerFactory.getLogger(InvoiceCoreClient.class);

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public InvoiceCoreClient(@Qualifier("agencyPlatformWebClient") WebClient webClient,
                             ObjectMapper objectMapper) {
        this.webClient = webClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<GeneratedInvoice> generate(InvoiceGenerationRequest request) {
        Map<String, Object> body = new HashMap<>();
        body.put("missionId", request.missionId());
        body.put("agencyId", request.agencyId().toString());
        body.put("amount", request.amount());
        body.put("currency", request.currency());
        body.put("description", request.description());
        if (request.clientId() != null) {
            body.put("clientId", request.clientId());
        }
        return webClient.post()
                .uri("/api/v1/billing/invoices/generate")
                .header("X-Tenant-Id", request.tenantId().toString())
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(this::toGenerated)
                .onErrorResume(e -> {
                    log.warn("[Invoice] generate failed missionId={}: {}", request.missionId(), e.getMessage());
                    return Mono.empty();
                });
    }

    @Override
    public Mono<String> getPdfUrl(UUID tenantId, String coreInvoiceId) {
        return webClient.get()
                .uri("/api/v1/billing/invoices/{id}/pdf", coreInvoiceId)
                .header("X-Tenant-Id", tenantId.toString())
                .retrieve()
                .bodyToMono(String.class)
                .flatMap(raw -> {
                    String url = parsePdfBody(raw);
                    return StringUtils.hasText(url) ? Mono.just(url) : Mono.empty();
                })
                .onErrorResume(e -> {
                    log.warn("[Invoice] PDF URL unavailable invoiceId={}: {}", coreInvoiceId, e.getMessage());
                    return Mono.empty();
                });
    }

    private String parsePdfBody(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("http") || trimmed.startsWith("/")) {
            // strip quotes if JSON-encoded string
            if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length() > 1) {
                return trimmed.substring(1, trimmed.length() - 1);
            }
            return trimmed;
        }
        try {
            return extractUrl(objectMapper.readTree(trimmed));
        } catch (Exception e) {
            return null;
        }
    }

    private GeneratedInvoice toGenerated(JsonNode node) {
        JsonNode data = node != null && node.has("data") ? node.get("data") : node;
        if (data == null || data.isNull()) {
            return new GeneratedInvoice(null, null, null, null, null, null);
        }
        return new GeneratedInvoice(
                text(data, "id"),
                firstText(data, "invoiceNumber", "number"),
                decimal(data, "totalAmount", "amount"),
                text(data, "currency"),
                text(data, "status"),
                firstText(data, "pdfUrl", "url")
        );
    }

    private String extractUrl(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        JsonNode data = node.has("data") ? node.get("data") : node;
        if (data != null && data.isTextual()) {
            return data.asText();
        }
        return firstText(data, "url", "pdfUrl", "downloadUrl");
    }

    private static String text(JsonNode n, String field) {
        return n != null && n.hasNonNull(field) ? n.get(field).asText() : null;
    }

    private static String firstText(JsonNode n, String... fields) {
        for (String f : fields) {
            String v = text(n, f);
            if (StringUtils.hasText(v)) {
                return v;
            }
        }
        return null;
    }

    private static BigDecimal decimal(JsonNode n, String... fields) {
        for (String f : fields) {
            if (n != null && n.hasNonNull(f)) {
                return new BigDecimal(n.get(f).asText());
            }
        }
        return null;
    }
}
