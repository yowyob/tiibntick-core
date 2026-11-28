package com.yowyob.tiibntick.core.accounting.adapter.out.kernel;

import com.fasterxml.jackson.databind.JsonNode;
import com.yowyob.tiibntick.core.accounting.application.port.out.KernelBookkeepingPort;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * {@link KernelBookkeepingPort} over the Kernel accounting service (OpenAPI {@code /api/accounting-service/accounts},
 * {@code /journals}, {@code /entries}, {@code /entries/{id}/validate}). The legacy paths ({@code /ecritures},
 * {@code /comptes}, {@code /plan-comptable}) are not used. Answers are read raw or inside the Kernel
 * {@code {success, data}} wrapper.
 */
@Component
public class KernelBookkeepingAdapter implements KernelBookkeepingPort {

    static final String BASE = "/api/accounting-service";
    static final String ORGANIZATION_HEADER = "X-Organization-Id";

    private static final com.fasterxml.jackson.databind.ObjectMapper JSON =
            new com.fasterxml.jackson.databind.ObjectMapper();

    private final WebClient kernel;

    public KernelBookkeepingAdapter(@Qualifier("kernelSettlementWebClient") WebClient kernel) {
        this.kernel = kernel;
    }

    @Override
    public Mono<UUID> findOrCreateAccount(UUID org, String number, String label, String type, UUID externalId) {
        return kernel.get().uri(b -> b.path(BASE + "/accounts/search").queryParam("query", number).build())
                .header(ORGANIZATION_HEADER, org.toString())
                .retrieve().bodyToMono(String.class).map(KernelBookkeepingAdapter::parse)
                .flatMapMany(KernelBookkeepingAdapter::items)
                .filter(a -> number.equals(a.path("accountNumber").asText()))
                .next()
                .map(KernelBookkeepingAdapter::id)
                .switchIfEmpty(Mono.defer(() -> {
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("accountNumber", number);
                    body.put("label", label);
                    body.put("accountType", type);
                    body.put("externalId", externalId);
                    return post("/accounts", org, body).map(KernelBookkeepingAdapter::id);
                }));
    }

    @Override
    public Mono<UUID> findOrCreateJournal(UUID org, String code, String label, String type) {
        return kernel.get().uri(BASE + "/journals")
                .header(ORGANIZATION_HEADER, org.toString())
                .retrieve().bodyToMono(String.class).map(KernelBookkeepingAdapter::parse)
                .flatMapMany(KernelBookkeepingAdapter::items)
                .filter(j -> code.equals(j.path("code").asText()))
                .next()
                .map(KernelBookkeepingAdapter::id)
                .switchIfEmpty(Mono.defer(() -> post("/journals", org,
                        Map.of("code", code, "label", label, "type", type)).map(KernelBookkeepingAdapter::id)));
    }

    @Override
    public Mono<KernelEntry> postEntry(UUID org, UUID journalId, String reference, Instant date,
                                       List<KernelLine> lines) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("journalId", journalId);
        body.put("reference", reference);
        body.put("entryDate", date.toString());
        body.put("lines", lines.stream().map(l -> Map.of("accountId", l.accountId(), "debit", l.debit(),
                "credit", l.credit(), "label", l.label())).toList());
        return post("/entries", org, body).map(KernelBookkeepingAdapter::entry);
    }

    @Override
    public Mono<KernelEntry> validateEntry(UUID org, UUID entryId) {
        return kernel.post().uri(BASE + "/entries/{id}/validate", entryId)
                .header(ORGANIZATION_HEADER, org.toString())
                .retrieve().bodyToMono(String.class).map(KernelBookkeepingAdapter::parse)
                .map(KernelBookkeepingAdapter::data)
                .map(KernelBookkeepingAdapter::entry);
    }

    @Override
    public Mono<KernelEntry> findEntryByReference(UUID org, String reference) {
        return kernel.get().uri(BASE + "/entries")
                .header(ORGANIZATION_HEADER, org.toString())
                .retrieve().bodyToMono(String.class).map(KernelBookkeepingAdapter::parse)
                .flatMapMany(KernelBookkeepingAdapter::items)
                .filter(e -> reference.equals(e.path("reference").asText()))
                .filter(e -> !"CANCELLED".equals(e.path("status").asText()))
                .next()
                .map(KernelBookkeepingAdapter::entry);
    }

    private Mono<JsonNode> post(String path, UUID org, Object body) {
        return Mono.fromCallable(() -> JSON.writeValueAsString(body)).flatMap(json -> kernel.post().uri(BASE + path)
                .header(ORGANIZATION_HEADER, org.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(json)
                .retrieve().bodyToMono(String.class).map(KernelBookkeepingAdapter::parse)
                .map(KernelBookkeepingAdapter::data));
    }

    /** Jackson 2 on purpose: the WebClient codecs are Jackson 3 (Spring 7), the module works with Jackson 2 trees. */
    static JsonNode parse(String body) {
        try {
            return JSON.readTree(body == null || body.isBlank() ? "null" : body);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Unreadable Kernel answer: " + e.getOriginalMessage(), e);
        }
    }

    static JsonNode data(JsonNode root) {
        return root != null && root.has("data") && (root.has("success") || root.size() <= 4) ? root.get("data")
                : root;
    }

    static Flux<JsonNode> items(JsonNode root) {
        JsonNode d = data(root);
        if (d != null && d.has("content")) {
            d = d.get("content");
        }
        return d != null && d.isArray() ? Flux.fromIterable(d) : Flux.empty();
    }

    static UUID id(JsonNode n) {
        String v = n.path("id").asText(null);
        if (v == null) {
            throw new IllegalStateException("The Kernel answered without an id: " + n);
        }
        return UUID.fromString(v);
    }

    static KernelEntry entry(JsonNode n) {
        return new KernelEntry(id(n), n.path("status").asText(null));
    }
}
