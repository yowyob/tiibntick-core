package com.yowyob.tiibntick.core.agency.fleet.adapter.out.clients;

import com.yowyob.tiibntick.core.agency.fleet.domain.vo.VehicleType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class ResourceCoreClient implements ResourceCorePort {

    private static final Logger log = LoggerFactory.getLogger(ResourceCoreClient.class);
    private static final String VEHICLES_PATH = "/api/resources/vehicles";
    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {};

    private final WebClient webClient;

    public ResourceCoreClient(@Qualifier("agencyPlatformWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    @Override
    public Mono<UUID> registerVehicle(RegisterVehicleRequest request) {
        double[] capacity = defaultCapacity(request.type());
        Map<String, Object> body = new HashMap<>();
        body.put("tenantId", request.tenantId());
        body.put("organizationId", request.kernelOrganizationId());
        body.put("agencyId", request.coreAgencyId());
        body.put("registrationNumber", request.registrationNumber());
        body.put("brand", request.brand());
        body.put("model", request.model());
        body.put("yearOfManufacture", request.yearOfManufacture());
        body.put("type", request.type().name());
        body.put("maxWeightKg", capacity[0]);
        body.put("maxVolumeM3", capacity[1]);
        body.put("hasRefrigeration", false);

        return webClient.post()
                .uri(VEHICLES_PATH)
                .header("X-Tenant-Id", request.tenantId().toString())
                .bodyValue(body)
                .retrieve()
                .bodyToMono(MAP_TYPE)
                .map(ResourceCoreClient::parseCoreEntityId)
                .doOnSuccess(id -> log.info("[Resource] Vehicle registered coreVehicleId={}", id))
                .doOnError(e -> log.warn("[Resource] Vehicle registration failed: {}", e.getMessage()));
    }

    @SuppressWarnings("unchecked")
    static UUID parseCoreEntityId(Map<String, Object> body) {
        if (body == null) {
            throw new IllegalStateException("Core resource response body is empty");
        }
        Object id = body.get("id");
        if (id instanceof String s) {
            return UUID.fromString(s);
        }
        if (id instanceof Map<?, ?> nested) {
            Object value = nested.get("value");
            if (value != null) {
                return UUID.fromString(value.toString());
            }
        }
        throw new IllegalStateException("Core resource response missing id field");
    }

    static double[] defaultCapacity(VehicleType type) {
        return switch (type) {
            case MOTORCYCLE -> new double[] {50, 0.1};
            case BICYCLE -> new double[] {20, 0.05};
            case TRICYCLE -> new double[] {100, 0.3};
            case CAR -> new double[] {200, 0.5};
            case VAN -> new double[] {500, 2.0};
            case TRUCK -> new double[] {2000, 10.0};
        };
    }
}
