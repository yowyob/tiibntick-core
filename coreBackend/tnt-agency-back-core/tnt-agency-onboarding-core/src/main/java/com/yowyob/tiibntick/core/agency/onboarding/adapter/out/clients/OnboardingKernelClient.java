package com.yowyob.tiibntick.core.agency.onboarding.adapter.out.clients;

import com.yowyob.tiibntick.core.agency.onboarding.config.AgencyOnboardingProperties;
import com.yowyob.tiibntick.core.agency.onboarding.domain.OnboardingApplication;
import com.yowyob.tiibntick.core.agency.org.adapter.in.web.dto.AgencyRegistryResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Platform onboarding gateway — orchestrates Kernel org provisioning via
 * TiiBnTick Core ({@code /api/v1/onboarding/agency/applications/{agencyRef}/**}).
 */
@Component
public class OnboardingKernelClient implements OnboardingKernelPort {

    private static final Logger log = LoggerFactory.getLogger(OnboardingKernelClient.class);
    private static final String ONBOARDING_BASE = "/api/v1/onboarding/agency/applications";
    private static final String LOGISTICS_SERVICE = "LOGISTICS";
    private static final String AGENCY_ADMIN_ROLE = "AGENCY_ADMIN";
    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {};

    private final WebClient webClient;
    private final AgencyOnboardingProperties onboardingProperties;
    private final boolean failOnError;

    public OnboardingKernelClient(
            @Qualifier("agencyPlatformWebClient") WebClient webClient,
            AgencyOnboardingProperties onboardingProperties,
            @Value("${tnt.security.dev-auth:false}") boolean devAuthEnabled) {
        this.webClient = webClient;
        this.onboardingProperties = onboardingProperties;
        this.failOnError = !devAuthEnabled;
    }

    @Override
    public Mono<UUID> onboardApplicantBusinessActor(AgencyRegistryResponse agency, OnboardingApplication app) {
        String ownerName = nullToEmpty(app.getOwnerName());
        if (ownerName.isBlank()) {
            ownerName = agency.name();
        }

        Map<String, Object> body = new HashMap<>();
        body.put("ownerName", ownerName);
        body.put("businessId", organizationCode(agency));
        body.put("isIndividual", true);

        return webClient.post()
                .uri(ONBOARDING_BASE + "/{agencyRef}/kernel-identity", agency.id())
                .bodyValue(body)
                .retrieve()
                .bodyToMono(MAP_TYPE)
                .map(root -> {
                    Map<String, Object> data = unwrapData(root);
                    UUID actorId = uuid(data, "kernelBusinessActorId");
                    if (actorId == null) {
                        actorId = uuid(data, "id");
                    }
                    if (actorId == null) {
                        throw new IllegalStateException("Core kernel-identity response missing actor id");
                    }
                    return actorId;
                });
    }

    @Override
    public Mono<ProvisionResult> provisionOrganization(ProvisionRequest request) {
        AgencyRegistryResponse agency = request.agency();
        OnboardingApplication app = request.application();
        UUID tenantId = agency.tenantId();

        return resolveBusinessActorId(agency, app)
                .flatMap(actorId -> approveViaCore(agency, app, actorId, request.defaultCurrency()))
                .onErrorResume(e -> handleProvisionError(tenantId, agency.id(), e));
    }

    @Override
    public Mono<Void> assignAgencyManagerRole(UUID agencyId, UUID tenantId, UUID organizationId,
                                              UUID ownerUserId, UUID reviewerId) {
        return webClient.post()
                .uri(ONBOARDING_BASE + "/{agencyRef}/assign-owner-role", agencyId)
                .bodyValue(Map.of(
                        "kernelOrganizationId", organizationId,
                        "ownerUserId", ownerUserId,
                        "roleCode", AGENCY_ADMIN_ROLE))
                .retrieve()
                .toBodilessEntity()
                .then()
                .onErrorResume(e -> handleError("assignAgencyManagerRole", tenantId, e));
    }

    private Mono<ProvisionResult> approveViaCore(
            AgencyRegistryResponse agency,
            OnboardingApplication app,
            UUID actorId,
            String defaultCurrency) {
        List<String> serviceCodes = new ArrayList<>();
        serviceCodes.addAll(onboardingProperties.getFinanceServiceCodes());
        serviceCodes.addAll(onboardingProperties.getHrmServiceCodes());

        String legalName = nullToEmpty(app.getLegalName());
        if (legalName.isBlank()) {
            legalName = agency.name();
        }

        String currency = defaultCurrency != null && !defaultCurrency.isBlank() ? defaultCurrency : "XAF";

        Map<String, Object> body = new HashMap<>();
        body.put("tenantId", agency.tenantId());
        body.put("businessActorId", actorId);
        body.put("code", organizationCode(agency));
        body.put("service", LOGISTICS_SERVICE);
        body.put("shortName", agency.name());
        body.put("longName", legalName);
        body.put("email", nullToEmpty(app.getOwnerEmail()));
        body.put("businessRegistrationNumber", nullToEmpty(agency.registrationNumber()));
        body.put("primaryCurrency", currency);
        body.put("provisionCommercial", onboardingProperties.isProvisionCommercialServices());
        body.put("commercialPlanCode", onboardingProperties.getCommercialPlan());
        body.put("serviceCodes", serviceCodes);
        body.put("approvalReason", "Approved by TiiBnTick Agency onboarding");
        body.put("ownerUserId", app.getApplicantUserId());
        body.put("ownerRoleCode", AGENCY_ADMIN_ROLE);

        return webClient.post()
                .uri(ONBOARDING_BASE + "/{agencyRef}/approve", agency.id())
                .bodyValue(body)
                .retrieve()
                .bodyToMono(MAP_TYPE)
                .map(OnboardingKernelClient::parseProvisionResult)
                .map(result -> {
                    if (result.businessActorId() == null) {
                        return new ProvisionResult(
                                result.organizationId(),
                                actorId,
                                result.coreAgencyId(),
                                result.tntRolesProvisioned(),
                                result.platformOptionsInitialized(),
                                result.ownerRoleAssignmentId());
                    }
                    return result;
                });
    }

    private static ProvisionResult parseProvisionResult(Map<String, Object> body) {
        Map<String, Object> data = unwrapData(body);
        UUID orgId = uuid(data, "kernelOrganizationId");
        if (orgId == null) {
            throw new IllegalStateException("Core approve response missing kernelOrganizationId");
        }
        return new ProvisionResult(
                orgId,
                uuid(data, "kernelBusinessActorId"),
                uuid(data, "coreAgencyId"),
                bool(data, "tntRolesProvisioned"),
                bool(data, "platformOptionsInitialized"),
                uuid(data, "ownerRoleAssignmentId"));
    }

    private Mono<UUID> resolveBusinessActorId(AgencyRegistryResponse agency, OnboardingApplication app) {
        if (agency.kernelBusinessActorId() != null) {
            return Mono.just(agency.kernelBusinessActorId());
        }
        if (app.getKernelBusinessActorId() != null) {
            return Mono.just(app.getKernelBusinessActorId());
        }
        return Mono.error(new IllegalStateException(
                "Identité Kernel candidat manquante. "
                        + "Le propriétaire doit compléter la phase 1 avant l'approbation admin."));
    }

    private Mono<ProvisionResult> handleProvisionError(UUID tenantId, UUID agencyId, Throwable e) {
        log.warn("[Core] provisionOrganization failed tenant={} agency={}: {}",
                tenantId, agencyId, e.getMessage());
        if (failOnError) {
            return Mono.error(new IllegalStateException(
                    "Approbation onboarding via Core échouée : " + e.getMessage(), e));
        }
        return Mono.empty();
    }

    private Mono<Void> handleError(String operation, UUID tenantId, Throwable e) {
        log.warn("[Core] {} failed for tenant {}: {}", operation, tenantId, e.getMessage());
        if (failOnError) {
            return Mono.error(new IllegalStateException(
                    "Core " + operation + " failed: " + e.getMessage(), e));
        }
        return Mono.empty();
    }

    private static String organizationCode(AgencyRegistryResponse agency) {
        String registration = agency.registrationNumber();
        if (registration != null && !registration.isBlank()) {
            return registration.trim().toUpperCase().replaceAll("[^A-Z0-9_-]", "-");
        }
        return "TNT-AGENCY-" + agency.id().toString().substring(0, 8).toUpperCase();
    }

    private static String nullToEmpty(String value) {
        return value != null ? value : "";
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> unwrapData(Map<String, Object> root) {
        if (root == null) {
            return Map.of();
        }
        Object data = root.get("data");
        if (data instanceof Map<?, ?> nested) {
            return (Map<String, Object>) nested;
        }
        return root;
    }

    private static UUID uuid(Map<String, Object> node, String field) {
        if (node == null) {
            return null;
        }
        Object value = node.get(field);
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value.toString());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static boolean bool(Map<String, Object> node, String field) {
        if (node == null) {
            return false;
        }
        Object value = node.get(field);
        if (value instanceof Boolean b) {
            return b;
        }
        if (value != null) {
            return Boolean.parseBoolean(value.toString());
        }
        return false;
    }
}
