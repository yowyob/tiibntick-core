package com.yowyob.tiibntick.core.agency.assignment.adapter.out.clients;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Component
public class DeliveryCoreClient implements DeliveryCorePort {

    private static final Logger log = LoggerFactory.getLogger(DeliveryCoreClient.class);
    private static final String BASE = "/api/v1/tenants/{tenantId}/deliveries";

    private final WebClient webClient;

    public DeliveryCoreClient(@Qualifier("agencyPlatformWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    @Override
    public Mono<DeliveryView> getById(UUID tenantId, UUID deliveryId) {
        return webClient.get()
                .uri(BASE + "/{deliveryId}", tenantId, deliveryId)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(this::toView)
                .doOnError(e -> log.warn("[DeliveryCore] getById failed deliveryId={}: {}", deliveryId, e.getMessage()));
    }

    @Override
    public Mono<DeliveryView> confirmPickup(UUID tenantId, UUID deliveryId) {
        return postTransition(tenantId, deliveryId, "/pickup", null, null);
    }

    @Override
    public Mono<DeliveryView> startTransit(UUID tenantId, UUID deliveryId, Double latitude, Double longitude) {
        Map<String, Object> body = null;
        if (latitude != null && longitude != null) {
            body = Map.of("latitude", latitude, "longitude", longitude);
        }
        return postTransition(tenantId, deliveryId, "/transit/start", body, null);
    }

    @Override
    public Mono<DeliveryView> depositAtRelay(UUID tenantId, UUID deliveryId, UUID relayPointId) {
        return webClient.post()
                .uri(BASE + "/{deliveryId}/relay/{relayPointId}/deposit",
                        tenantId, deliveryId, relayPointId)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(this::toView)
                .doOnError(e -> log.warn("[DeliveryCore] depositAtRelay failed deliveryId={}: {}",
                        deliveryId, e.getMessage()));
    }

    @Override
    public Mono<DeliveryView> complete(UUID tenantId, UUID deliveryId, String proofPhotoUrl) {
        return postTransition(tenantId, deliveryId, "/complete", null, proofPhotoUrl);
    }

    @Override
    public Mono<DeliveryView> fail(UUID tenantId, UUID deliveryId, String reason) {
        return webClient.post()
                .uri(uri -> {
                    var builder = uri.path(BASE + "/{deliveryId}/fail");
                    if (StringUtils.hasText(reason)) {
                        builder.queryParam("reason", reason);
                    }
                    return builder.build(tenantId, deliveryId);
                })
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(this::toView)
                .doOnError(e -> log.warn("[DeliveryCore] fail failed deliveryId={}: {}", deliveryId, e.getMessage()));
    }

    @Override
    public Mono<Void> cancel(UUID tenantId, UUID deliveryId, String reason) {
        return webClient.post()
                .uri(uri -> {
                    var builder = uri.path(BASE + "/{deliveryId}/cancel");
                    if (StringUtils.hasText(reason)) {
                        builder.queryParam("reason", reason);
                    }
                    return builder.build(tenantId, deliveryId);
                })
                .retrieve()
                .toBodilessEntity()
                .then()
                .doOnError(e -> log.warn("[DeliveryCore] cancel failed deliveryId={}: {}", deliveryId, e.getMessage()));
    }

    private Mono<DeliveryView> postTransition(
            UUID tenantId,
            UUID deliveryId,
            String suffix,
            Map<String, Object> body,
            String queryProofUrl) {
        return webClient.post()
                .uri(uri -> {
                    var builder = uri.path(BASE + "/{deliveryId}" + suffix);
                    if (StringUtils.hasText(queryProofUrl)) {
                        builder.queryParam("proofPhotoUrl", queryProofUrl);
                    }
                    return builder.build(tenantId, deliveryId);
                })
                .bodyValue(body != null ? body : Map.of())
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(this::toView)
                .doOnError(e -> log.warn("[DeliveryCore] {} failed deliveryId={}: {}",
                        suffix, deliveryId, e.getMessage()));
    }

    private DeliveryView toView(JsonNode node) {
        JsonNode data = node.has("data") ? node.get("data") : node;
        return new DeliveryView(
                uuid(data, "id"),
                text(data, "status"),
                instant(data, "actualPickupTime"),
                instant(data, "actualDeliveryTime"),
                uuid(data, "deliveryPersonId"));
    }

    private static UUID uuid(JsonNode node, String field) {
        if (node == null || !node.hasNonNull(field)) {
            return null;
        }
        return UUID.fromString(node.get(field).asText());
    }

    private static String text(JsonNode node, String field) {
        return node != null && node.has(field) ? node.get(field).asText(null) : null;
    }

    private static Instant instant(JsonNode node, String field) {
        if (node == null || !node.hasNonNull(field)) {
            return null;
        }
        return Instant.parse(node.get(field).asText());
    }
}
