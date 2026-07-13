package com.yowyob.tiibntick.core.agency.onboarding.adapter.out.clients;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
public class AdministrationCoreClient implements AdministrationCorePort {

    private static final Logger log = LoggerFactory.getLogger(AdministrationCoreClient.class);

    private static final String PROVISION_PATH = "/api/v1/admin/role-templates/provision";
    private static final String INIT_OPTIONS_PATH = "/api/v1/admin/settings/tnt-platform-options/initialize";

    private final WebClient webClient;
    private final boolean failOnError;

    public AdministrationCoreClient(
            @Qualifier("agencyPlatformWebClient") WebClient webClient,
            @Value("${tnt.security.dev-auth:false}") boolean devAuthEnabled) {
        this.webClient = webClient;
        this.failOnError = !devAuthEnabled;
    }

    @Override
    public Mono<Void> provisionRoleTemplates(UUID tenantId, UUID organizationId, UUID actorUserId) {
        return webClient.post()
                .uri(PROVISION_PATH)
                .header("X-Tenant-Id", tenantId.toString())
                .header("X-User-Id", actorUserId.toString())
                .header("X-Organization-Id", organizationId.toString())
                .retrieve()
                .toBodilessEntity()
                .then()
                .onErrorResume(e -> handleError("provisionRoleTemplates", tenantId, e));
    }

    @Override
    public Mono<Void> initializePlatformOptions(UUID tenantId) {
        return webClient.post()
                .uri(INIT_OPTIONS_PATH)
                .header("X-Tenant-Id", tenantId.toString())
                .retrieve()
                .toBodilessEntity()
                .then()
                .onErrorResume(e -> handleError("initializePlatformOptions", tenantId, e));
    }

    private Mono<Void> handleError(String operation, UUID tenantId, Throwable e) {
        log.warn("[AdministrationCore] {} failed for tenant {}: {}", operation, tenantId, e.getMessage());
        if (failOnError) {
            return Mono.error(new IllegalStateException(
                    "Core administration " + operation + " failed: " + e.getMessage(), e));
        }
        return Mono.empty();
    }
}
