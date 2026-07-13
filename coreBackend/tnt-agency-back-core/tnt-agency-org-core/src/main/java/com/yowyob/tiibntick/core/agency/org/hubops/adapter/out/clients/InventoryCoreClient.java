package com.yowyob.tiibntick.core.agency.org.hubops.adapter.out.clients;

import com.yowyob.tiibntick.common.exception.TntValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

@Component
public class InventoryCoreClient implements InventoryCorePort {

    private static final Logger log = LoggerFactory.getLogger(InventoryCoreClient.class);
    private static final String BASE = "/api/v1/inventory";
    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {};

    private final WebClient webClient;

    public InventoryCoreClient(@Qualifier("agencyPlatformWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    @Override
    public Mono<HubPackageView> depositPackage(DepositPackageRequest request) {
        Map<String, Object> body = Map.of(
                "packageId", request.packageId().toString(),
                "trackingCode", request.trackingCode(),
                "storageLocation", request.storageLocation() != null ? request.storageLocation() : "",
                "depositedByActorId", request.depositedByActorId() != null
                        ? request.depositedByActorId().toString() : "",
                "recipientPhone", request.recipientPhone() != null ? request.recipientPhone() : ""
        );
        return webClient.post()
                .uri(BASE + "/hubs/{hubId}/packages", request.coreHubId())
                .header("X-Tenant-Id", request.tenantId().toString())
                .bodyValue(body)
                .retrieve()
                .bodyToMono(MAP_TYPE)
                .map(this::toView)
                .doOnError(e -> log.warn("[InventoryCore] deposit failed hub={}: {}",
                        request.coreHubId(), e.getMessage()));
    }

    @Override
    public Mono<Void> pickupPackage(PickupPackageRequest request) {
        return webClient.post()
                .uri(BASE + "/hub-packages/{trackingCode}/pickup", request.trackingCode())
                .bodyValue(Map.of("pickedUpByActorId", request.pickedUpByActorId().toString()))
                .retrieve()
                .toBodilessEntity()
                .then()
                .doOnError(e -> log.warn("[InventoryCore] pickup failed code={}: {}",
                        request.trackingCode(), e.getMessage()));
    }

    @Override
    public Mono<Double> getOccupancyRate(UUID tenantId, UUID coreHubId) {
        return webClient.get()
                .uri(BASE + "/hubs/{hubId}/occupancy", coreHubId)
                .header("X-Tenant-Id", tenantId.toString())
                .retrieve()
                .bodyToMono(MAP_TYPE)
                .map(node -> {
                    Map<String, Object> root = unwrap(node);
                    Object rate = root.get("occupancyRate");
                    if (rate instanceof Number n) {
                        return Math.max(0.0, Math.min(1.0, n.doubleValue()));
                    }
                    return 0.0;
                })
                .onErrorResume(e -> {
                    log.warn("[InventoryCore] occupancy failed hub={}: {}", coreHubId, e.getMessage());
                    return Mono.empty();
                });
    }

    @SuppressWarnings("unchecked")
    private HubPackageView toView(Map<String, Object> node) {
        Map<String, Object> root = unwrap(node);
        UUID id = uuid(root, "id");
        if (id == null) {
            throw new TntValidationException("Réponse Core inventory invalide (id manquant).");
        }
        return new HubPackageView(
                id,
                uuid(root, "tenantId"),
                uuid(root, "hubId"),
                uuid(root, "packageId"),
                text(root, "trackingCode"),
                text(root, "status"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> unwrap(Map<String, Object> root) {
        if (root == null) {
            return Map.of();
        }
        Object data = root.get("data");
        if (data instanceof Map<?, ?> nested) {
            return (Map<String, Object>) nested;
        }
        return root;
    }

    private static String text(Map<String, Object> node, String field) {
        if (node == null) {
            return null;
        }
        Object value = node.get(field);
        return value != null ? value.toString() : null;
    }

    private static UUID uuid(Map<String, Object> node, String field) {
        String raw = text(node, field);
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
