package com.yowyob.tiibntick.core.agency.org.adapter.out.clients;

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

/**
 * HTTP adapter — tnt-organization-core via platform bootstrap.
 * <ul>
 *   <li>{@code POST /api/v1/tenants/{tenantId}/agencies}</li>
 *   <li>{@code POST /api/v1/tenants/{tenantId}/agencies/{coreAgencyId}/branches}</li>
 *   <li>{@code POST /api/v1/tenants/{tenantId}/hubs}</li>
 * </ul>
 */
@Component
public class OrganizationCoreClient implements OrganizationCorePort {

    private static final Logger log = LoggerFactory.getLogger(OrganizationCoreClient.class);
    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {};

    private final WebClient webClient;

    public OrganizationCoreClient(@Qualifier("agencyPlatformWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    @Override
    public Mono<UUID> registerAgency(RegisterAgencyRequest request) {
        Map<String, Object> body = new HashMap<>();
        body.put("organizationId", request.kernelOrganizationId());
        body.put("name", request.name());
        body.put("commerceRegistryNumber", request.commerceRegistryNumber());
        body.put("primaryCurrency", request.primaryCurrency() != null ? request.primaryCurrency() : "XAF");

        return webClient.post()
                .uri("/api/v1/tenants/{tenantId}/agencies", request.tenantId())
                .header("X-Tenant-Id", request.tenantId().toString())
                .bodyValue(body)
                .retrieve()
                .bodyToMono(MAP_TYPE)
                .map(OrganizationCoreClient::parseCoreEntityId)
                .doOnSuccess(id -> log.info("[OrgCore] Agency registered tenantId={} coreAgencyId={}",
                        request.tenantId(), id))
                .doOnError(e -> log.warn("[OrgCore] Agency registration failed tenantId={}: {}",
                        request.tenantId(), e.getMessage()));
    }

    @Override
    public Mono<UUID> registerBranch(RegisterBranchRequest request) {
        Map<String, Object> body = new HashMap<>();
        body.put("organizationId", request.kernelOrganizationId());
        body.put("name", request.name());
        body.put("address", request.address() != null ? request.address() : "");

        return webClient.post()
                .uri("/api/v1/tenants/{tenantId}/agencies/{coreAgencyId}/branches",
                        request.tenantId(), request.coreAgencyId())
                .header("X-Tenant-Id", request.tenantId().toString())
                .bodyValue(body)
                .retrieve()
                .bodyToMono(MAP_TYPE)
                .map(OrganizationCoreClient::parseCoreEntityId)
                .doOnSuccess(id -> log.info("[OrgCore] Branch registered coreAgencyId={} coreBranchId={}",
                        request.coreAgencyId(), id))
                .doOnError(e -> log.warn("[OrgCore] Branch registration failed coreAgencyId={}: {}",
                        request.coreAgencyId(), e.getMessage()));
    }

    @Override
    public Mono<UUID> registerHub(RegisterHubRequest request) {
        Map<String, Object> body = new HashMap<>();
        body.put("organizationId", request.kernelOrganizationId());
        body.put("name", request.name());
        body.put("maxParcelCapacity", request.maxParcelCapacity());
        body.put("geographicPointWkt", request.geographicPointWkt());
        body.put("openingHours", request.openingHours());

        return webClient.post()
                .uri("/api/v1/tenants/{tenantId}/hubs", request.tenantId())
                .header("X-Tenant-Id", request.tenantId().toString())
                .bodyValue(body)
                .retrieve()
                .bodyToMono(MAP_TYPE)
                .map(OrganizationCoreClient::parseCoreEntityId)
                .doOnSuccess(id -> log.info("[OrgCore] Hub registered tenantId={} coreHubId={}",
                        request.tenantId(), id))
                .doOnError(e -> log.warn("[OrgCore] Hub registration failed tenantId={}: {}",
                        request.tenantId(), e.getMessage()));
    }

    @SuppressWarnings("unchecked")
    static UUID parseCoreEntityId(Map<String, Object> body) {
        if (body == null) {
            throw new IllegalStateException("Core organization response body is empty");
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
        throw new IllegalStateException("Core organization response missing id field");
    }
}
