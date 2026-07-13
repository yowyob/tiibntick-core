package com.yowyob.tiibntick.core.agency.assignment.adapter.out.clients;

import com.yowyob.tiibntick.common.exception.TntValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Creates delivery missions via platform Core:
 * {@code POST /api/sales/orders} then {@code POST /api/sales/orders/{id}/dispatch}.
 */
@Component
public class DeliveryMissionClient implements DeliveryMissionPort {

    private static final Logger log = LoggerFactory.getLogger(DeliveryMissionClient.class);
    private static final String ORDERS_PATH = "/api/sales/orders";

    private final WebClient webClient;
    private final UUID defaultProductId;

    public DeliveryMissionClient(
            @Qualifier("agencyPlatformWebClient") WebClient webClient,
            @Value("${tnt.agency.missions.default-product-id:00000000-0000-0000-0000-000000000101}")
            UUID defaultProductId) {
        this.webClient = webClient;
        this.defaultProductId = defaultProductId;
    }

    @Override
    public Mono<CreatedCoreMission> createMission(CreateMissionRequest request) {
        if (request.organizationId() == null) {
            return Mono.error(new TntValidationException(
                    "kernelOrganizationId requis pour créer une mission Core."));
        }
        if (request.coreAgencyId() == null) {
            return Mono.error(new TntValidationException(
                    "coreAgencyId requis — synchroniser l'agence avec le Core d'abord."));
        }

        UUID missionId = request.missionId() != null ? request.missionId() : UUID.randomUUID();
        Map<String, Object> orderBody = buildOrderBody(request);

        return webClient.post()
                .uri(ORDERS_PATH)
                .header("X-Tenant-Id", request.tenantId().toString())
                .header("X-Organization-Id", request.organizationId().toString())
                .header("X-Agency-Id", request.coreAgencyId().toString())
                .bodyValue(orderBody)
                .retrieve()
                .bodyToMono(CoreSalesOrderResponse.class)
                .flatMap(order -> dispatchOrder(request, order.id(), missionId)
                        .thenReturn(new CreatedCoreMission(missionId, order.orderNumber())))
                .onErrorResume(e -> {
                    log.warn("[DeliveryMission] sales order failed agencyId={}: {}",
                            request.agencyId(), e.getMessage());
                    return Mono.error(new TntValidationException(
                            "Création mission via Core échouée : " + e.getMessage()));
                });
    }

    private Mono<Void> dispatchOrder(CreateMissionRequest request, UUID orderId, UUID missionId) {
        return webClient.post()
                .uri(ORDERS_PATH + "/{orderId}/dispatch", orderId)
                .header("X-Tenant-Id", request.tenantId().toString())
                .header("X-Organization-Id", request.organizationId().toString())
                .header("X-Agency-Id", request.coreAgencyId().toString())
                .bodyValue(Map.of("missionId", missionId.toString()))
                .retrieve()
                .toBodilessEntity()
                .then();
    }

    private Map<String, Object> buildOrderBody(CreateMissionRequest request) {
        double weightKg = request.weightKg() != null ? request.weightKg() : 1.0;
        Map<String, Object> deliveryAddress = new HashMap<>();
        deliveryAddress.put("street", nullToEmpty(request.deliveryAddress()));
        deliveryAddress.put("quartier", "");
        deliveryAddress.put("city", "Douala");
        deliveryAddress.put("country", "CM");
        deliveryAddress.put("landmark", nullToEmpty(request.pickupAddress()));
        deliveryAddress.put("recipientName", nullToEmpty(request.recipientName()));
        deliveryAddress.put("recipientPhone", nullToEmpty(request.recipientPhone()));

        Map<String, Object> line = Map.of(
                "productId", defaultProductId,
                "productName", "Agency delivery mission",
                "sku", "AGY-DELIVERY",
                "quantity", BigDecimal.ONE,
                "unitPrice", BigDecimal.valueOf(Math.max(1.0, weightKg * 100)),
                "currency", "XAF",
                "notes", "Created by tnt-agency-back-core"
        );

        Map<String, Object> body = new HashMap<>();
        body.put("clientThirdPartyId", request.agencyId());
        body.put("lines", List.of(line));
        body.put("deliveryAddress", deliveryAddress);
        body.put("priority", "NORMAL");
        body.put("currency", "XAF");
        body.put("providerOrgType", "AGENCY");
        body.put("providerOrgId", request.coreAgencyId().toString());
        return body;
    }

    private static String nullToEmpty(String value) {
        return value != null ? value : "";
    }

    private record CoreSalesOrderResponse(UUID id, String orderNumber) {}
}
