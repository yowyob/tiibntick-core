package com.yowyob.tiibntick.core.administration.onboarding.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yowyob.tiibntick.core.administration.application.service.TntAdministrationApplicationService;
import com.yowyob.tiibntick.core.administration.onboarding.application.port.in.AgencyOnboardingUseCase;
import com.yowyob.tiibntick.core.administration.onboarding.application.port.out.IKernelOnboardingGatewayPort;
import com.yowyob.tiibntick.core.administration.onboarding.domain.exception.AgencyOnboardingException;
import com.yowyob.tiibntick.core.administration.onboarding.domain.model.AgencyKernelIdentityRequest;
import com.yowyob.tiibntick.core.administration.onboarding.domain.model.AgencyKernelIdentityResponse;
import com.yowyob.tiibntick.core.administration.onboarding.domain.model.ApproveAgencyOnboardingRequest;
import com.yowyob.tiibntick.core.administration.onboarding.domain.model.ApproveAgencyOnboardingResponse;
import com.yowyob.tiibntick.core.administration.onboarding.domain.model.AssignAgencyOwnerRoleRequest;
import com.yowyob.tiibntick.core.administration.onboarding.domain.model.AssignAgencyOwnerRoleResponse;
import com.yowyob.tiibntick.core.administration.onboarding.domain.model.KernelEnvelope;
import com.yowyob.tiibntick.core.administration.onboarding.domain.model.ProvisionAgencyOrganizationRequest;
import com.yowyob.tiibntick.core.administration.onboarding.domain.model.ProvisionAgencyOrganizationResponse;
import com.yowyob.tiibntick.core.organization.application.port.in.ManageAgencyUseCase;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implements {@link AgencyOnboardingUseCase} — orchestrates the Kernel HTTP calls (via
 * {@link IKernelOnboardingGatewayPort}) plus the in-process TiiBnTick steps
 * ({@link ManageAgencyUseCase#createAgency}, {@link TntAdministrationApplicationService})
 * that together onboard a new agency, per {@code CORE_KERNEL_GATEWAY_SPEC.md} §8.
 *
 * <p><b>Authorization boundary:</b> these operations are reached only through
 * {@code PlatformAgencyOnboardingController} (public paths, platform
 * {@code X-Client-Id}/{@code X-Api-Key} required — see
 * {@code TntAuthGatewaySecurityConfig}), never through TiiBnTick's own
 * {@code @PreAuthorize}/JWT-authenticated chain: at Phase 1/2 the organization/agency
 * doesn't exist yet, so no TiiBnTick RBAC permission could apply. The calling platform is
 * trusted to have authorized its own end user before invoking this gateway — matching
 * {@code CORE_KERNEL_GATEWAY_SPEC.md}'s own trust model (§10).
 *
 * @author MANFOUO Braun
 */
@Service
public class AgencyOnboardingOrchestratorService implements AgencyOnboardingUseCase {

    private final IKernelOnboardingGatewayPort kernelPort;
    private final ObjectMapper objectMapper;
    private final ManageAgencyUseCase manageAgencyUseCase;
    private final TntAdministrationApplicationService administrationService;

    public AgencyOnboardingOrchestratorService(
            IKernelOnboardingGatewayPort kernelPort,
            ObjectMapper objectMapper,
            ManageAgencyUseCase manageAgencyUseCase,
            TntAdministrationApplicationService administrationService) {
        this.kernelPort = kernelPort;
        this.objectMapper = objectMapper;
        this.manageAgencyUseCase = manageAgencyUseCase;
        this.administrationService = administrationService;
    }

    // ── Phase 1 ───────────────────────────────────────────────────────────────

    @Override
    public Mono<AgencyKernelIdentityResponse> createKernelIdentity(AgencyKernelIdentityRequest request, String candidateBearerAuthorization) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("name", request.ownerName());
        if (request.businessId() != null) {
            body.put("businessId", request.businessId());
        }
        body.put("role", "OWNER");
        body.put("type", "BUSINESS");
        body.put("isIndividual", request.isIndividual());
        body.put("isAvailable", true);
        body.put("isVerified", true);
        body.put("isActive", true);

        return kernelPort.invoke(HttpMethod.POST, "/api/actors/onboarding", body, candidateBearerAuthorization)
                .flatMap(env -> requireSuccess(env, "actors.onboarding"))
                .map(env -> new AgencyKernelIdentityResponse(uuidField(env.data(), "id")));
    }

    // ── Phase 2 ───────────────────────────────────────────────────────────────

    @Override
    public Mono<ProvisionAgencyOrganizationResponse> provisionOrganization(ProvisionAgencyOrganizationRequest request, String adminBearerAuthorization) {
        return doProvisionOrganization(request, adminBearerAuthorization);
    }

    private Mono<ProvisionAgencyOrganizationResponse> doProvisionOrganization(ProvisionAgencyOrganizationRequest request, String bearer) {
        ObjectNode createBody = objectMapper.createObjectNode();
        createBody.put("businessActorId", request.businessActorId().toString());
        createBody.put("code", request.code());
        createBody.put("service", request.service());
        createBody.put("shortName", request.shortName());
        createBody.put("longName", request.longName());
        if (request.email() != null) {
            createBody.put("email", request.email());
        }
        if (request.businessRegistrationNumber() != null) {
            createBody.put("businessRegistrationNumber", request.businessRegistrationNumber());
        }

        return kernelPort.invoke(HttpMethod.POST, "/api/organizations", createBody, bearer)
                .flatMap(env -> requireSuccess(env, "organizations.create"))
                .map(env -> uuidField(env.data(), "id"))
                .flatMap(organizationId -> approveOrganization(organizationId, request.approvalReason(), bearer)
                        .then(subscribeCommercialIfRequested(organizationId, request, bearer))
                        .then(subscribeServices(organizationId, request.serviceCodes(), bearer))
                        .thenReturn(new ProvisionAgencyOrganizationResponse(organizationId, request.businessActorId())));
    }

    private Mono<Void> approveOrganization(UUID organizationId, String reason, String bearer) {
        ObjectNode body = objectMapper.createObjectNode();
        if (reason != null) {
            body.put("reason", reason);
        }
        return kernelPort.invoke(HttpMethod.POST, "/api/organizations/" + organizationId + "/approve", body, bearer)
                .flatMap(env -> requireSuccess(env, "organizations.approve"))
                .then();
    }

    private Mono<Void> subscribeCommercialIfRequested(UUID organizationId, ProvisionAgencyOrganizationRequest request, String bearer) {
        if (!request.provisionCommercial() || request.commercialPlanCode() == null) {
            return Mono.empty();
        }
        ObjectNode body = objectMapper.createObjectNode();
        body.put("planCode", request.commercialPlanCode());
        return kernelPort.invoke(HttpMethod.POST, "/api/organizations/" + organizationId + "/commercial-subscriptions", body, bearer)
                .flatMap(env -> requireSuccess(env, "organizations.commercial-subscriptions"))
                .then();
    }

    private Mono<Void> subscribeServices(UUID organizationId, List<String> serviceCodes, String bearer) {
        if (serviceCodes == null || serviceCodes.isEmpty()) {
            return Mono.empty();
        }
        return Flux.fromIterable(serviceCodes)
                .concatMap(code -> {
                    ObjectNode body = objectMapper.createObjectNode();
                    body.put("serviceCode", code);
                    return kernelPort.invoke(HttpMethod.POST, "/api/organizations/" + organizationId + "/services", body, bearer)
                            .flatMap(env -> requireSuccess(env, "organizations.services[" + code + "]"));
                })
                .then();
    }

    // ── Phase 2b ──────────────────────────────────────────────────────────────

    @Override
    public Mono<AssignAgencyOwnerRoleResponse> assignOwnerRole(AssignAgencyOwnerRoleRequest request, String adminBearerAuthorization) {
        return doAssignOwnerRole(request, adminBearerAuthorization);
    }

    private Mono<AssignAgencyOwnerRoleResponse> doAssignOwnerRole(AssignAgencyOwnerRoleRequest request, String bearer) {
        String roleCode = request.roleCode() != null ? request.roleCode() : "AGENCY_ADMIN";
        // Best-effort per CORE_KERNEL_GATEWAY_SPEC.md §8.3 step 5 — ignore failure (roles may already exist).
        return kernelPort.invoke(HttpMethod.POST, "/api/administration/roles/defaults", objectMapper.createObjectNode(), bearer)
                .onErrorResume(e -> Mono.empty())
                .then(kernelPort.invoke(HttpMethod.GET, "/api/administration/roles", null, bearer))
                .flatMap(env -> requireSuccess(env, "administration.roles.list"))
                .flatMap(env -> findRoleIdByCode(env.data(), roleCode)
                        .map(Mono::just)
                        .orElseGet(() -> Mono.error(AgencyOnboardingException.ownerRoleNotFound(roleCode))))
                .flatMap(roleId -> {
                    ObjectNode body = objectMapper.createObjectNode();
                    body.put("roleId", roleId.toString());
                    body.put("scopeType", "ORGANIZATION");
                    body.put("scopeId", request.kernelOrganizationId().toString());
                    body.put("scope", "ORGANIZATION:" + request.kernelOrganizationId());
                    return kernelPort.invoke(HttpMethod.POST, "/api/administration/users/" + request.ownerUserId() + "/roles", body, bearer)
                            .flatMap(env -> requireSuccess(env, "administration.users.roles.assign"))
                            .map(env -> new AssignAgencyOwnerRoleResponse(roleId, uuidField(env.data(), "id")));
                });
    }

    // ── Composite: approve ───────────────────────────────────────────────────

    @Override
    public Mono<ApproveAgencyOnboardingResponse> approve(ApproveAgencyOnboardingRequest request, String adminBearerAuthorization) {
        ProvisionAgencyOrganizationRequest provisionRequest = new ProvisionAgencyOrganizationRequest(
                request.tenantId(), request.businessActorId(), request.code(), request.service(),
                request.shortName(), request.longName(), request.email(), request.businessRegistrationNumber(),
                request.provisionCommercial(), request.commercialPlanCode(), request.serviceCodes(), request.approvalReason());

        return doProvisionOrganization(provisionRequest, adminBearerAuthorization)
                .flatMap(provisioned -> createTntAgency(request, provisioned)
                        .flatMap(agencyId -> bootstrapTenant(request.tenantId(), provisioned, agencyId)
                                .flatMap(rolesAndOptions -> maybeAssignOwnerRole(request, provisioned, adminBearerAuthorization)
                                        .map(ownerAssignmentId -> new ApproveAgencyOnboardingResponse(
                                                provisioned.kernelOrganizationId(),
                                                provisioned.kernelBusinessActorId(),
                                                agencyId,
                                                rolesAndOptions.tntRolesProvisioned(),
                                                rolesAndOptions.platformOptionsInitialized(),
                                                ownerAssignmentId.orElse(null))))));
    }

    private Mono<UUID> createTntAgency(ApproveAgencyOnboardingRequest request, ProvisionAgencyOrganizationResponse provisioned) {
        return manageAgencyUseCase.createAgency(
                        provisioned.kernelOrganizationId(), request.tenantId(), request.shortName(),
                        request.businessRegistrationNumber(), request.primaryCurrency())
                .map(agency -> agency.getId().value());
    }

    private record RolesAndOptions(boolean tntRolesProvisioned, boolean platformOptionsInitialized) {
    }

    private Mono<RolesAndOptions> bootstrapTenant(UUID tenantId, ProvisionAgencyOrganizationResponse provisioned, UUID agencyId) {
        return administrationService.provisionForTenant(tenantId, provisioned.kernelOrganizationId(), agencyId)
                .thenReturn(true)
                .onErrorResume(e -> Mono.just(false))
                .flatMap(rolesOk -> administrationService.initializeDefaultOptions(tenantId)
                        .thenReturn(true)
                        .onErrorResume(e -> Mono.just(false))
                        .map(optionsOk -> new RolesAndOptions(rolesOk, optionsOk)));
    }

    private Mono<Optional<UUID>> maybeAssignOwnerRole(ApproveAgencyOnboardingRequest request, ProvisionAgencyOrganizationResponse provisioned, String bearer) {
        if (request.ownerUserId() == null) {
            return Mono.just(Optional.empty());
        }
        return doAssignOwnerRole(
                new AssignAgencyOwnerRoleRequest(provisioned.kernelOrganizationId(), request.ownerUserId(), request.ownerRoleCode()),
                bearer)
                .map(result -> Optional.of(result.assignmentId()));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Mono<KernelEnvelope> requireSuccess(KernelEnvelope envelope, String step) {
        if (envelope.failed()) {
            return Mono.error(AgencyOnboardingException.kernelStepFailed(step, envelope.errorCode(), envelope.message()));
        }
        return Mono.just(envelope);
    }

    private static UUID uuidField(JsonNode data, String field) {
        if (data == null) {
            return null;
        }
        JsonNode value = data.get(field);
        return value != null && !value.isNull() ? UUID.fromString(value.asText()) : null;
    }

    private static Optional<UUID> findRoleIdByCode(JsonNode data, String roleCode) {
        if (data == null || !data.isArray()) {
            return Optional.empty();
        }
        for (JsonNode role : data) {
            String code = role.path("code").asText(null);
            if (roleCode.equalsIgnoreCase(code)) {
                return Optional.ofNullable(uuidField(role, "id"));
            }
        }
        return Optional.empty();
    }
}
