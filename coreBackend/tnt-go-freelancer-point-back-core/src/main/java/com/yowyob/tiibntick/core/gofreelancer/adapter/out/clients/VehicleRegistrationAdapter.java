package com.yowyob.tiibntick.core.gofreelancer.adapter.out.clients;

import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IVehicleRegistrationPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

/**
 * Outbound adapter implementing IVehicleRegistrationPort.
 * Registers vehicles in tnt-resource-core via HTTP.
 *
 * @author François-Charles ATANGA
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VehicleRegistrationAdapter implements IVehicleRegistrationPort {

    private final WebClient.Builder webClientBuilder;

    @Value("${tnt.resource-core.base-url:http://localhost:8084}")
    private String resourceCoreBaseUrl;

    @Override
    public Mono<UUID> registerVehicle(
            UUID tenantId,
            UUID organizationId,
            UUID agencyId,
            String registrationNumber,
            String brand,
            String model,
            int yearOfManufacture,
            String type,
            double maxWeightKg,
            double maxVolumeM3) {

        Map<String, Object> body = Map.of(
                "tenantId", tenantId.toString(),
                "organizationId", organizationId.toString(),
                "agencyId", agencyId.toString(),
                "registrationNumber", registrationNumber,
                "brand", brand != null ? brand : "",
                "model", model != null ? model : "",
                "yearOfManufacture", yearOfManufacture,
                "type", type != null ? type : "CAR",
                "maxWeightKg", maxWeightKg,
                "maxVolumeM3", maxVolumeM3
        );

        return webClientBuilder.baseUrl(resourceCoreBaseUrl).build()
                .post()
                .uri("/api/v1/vehicles")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .map(UUID::fromString)
                .onErrorResume(e -> {
                    log.error("Failed to register vehicle in resource-core: {}", e.getMessage());
                    // Return a locally generated ID so registration flow is not blocked
                    return Mono.just(UUID.randomUUID());
                });
    }
}
