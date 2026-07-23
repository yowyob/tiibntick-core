package com.yowyob.tiibntick.core.agency.onboarding.application.service;

import com.yowyob.tiibntick.common.exception.TntConflictException;
import com.yowyob.tiibntick.common.exception.TntNotFoundException;
import com.yowyob.tiibntick.common.exception.TntUnauthorizedException;
import com.yowyob.tiibntick.common.exception.TntValidationException;
import com.yowyob.tiibntick.core.agency.onboarding.adapter.in.web.dto.OnboardingDetailResponse;
import com.yowyob.tiibntick.core.agency.onboarding.adapter.in.web.dto.OnboardingListItemResponse;
import com.yowyob.tiibntick.core.agency.onboarding.adapter.out.clients.AdministrationCorePort;
import com.yowyob.tiibntick.core.agency.onboarding.adapter.out.clients.OnboardingKernelPort;
import com.yowyob.tiibntick.core.agency.onboarding.adapter.out.persistence.OnboardingApplicationR2dbcRepository;
import com.yowyob.tiibntick.core.agency.onboarding.application.mapper.OnboardingMapper;
import com.yowyob.tiibntick.core.agency.onboarding.domain.OnboardingApplication;
import com.yowyob.tiibntick.core.agency.org.adapter.in.web.dto.AgencyRegistryResponse;
import com.yowyob.tiibntick.core.agency.org.adapter.out.persistence.entity.AgencySettingsEntity;
import com.yowyob.tiibntick.core.agency.org.application.mapper.AgencyOrgMapper;
import com.yowyob.tiibntick.core.agency.org.application.service.AgencyRegistryService;
import com.yowyob.tiibntick.core.agency.staff.application.service.StaffMemberService;
import com.yowyob.tiibntick.core.agency.staff.domain.vo.StaffRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Port of tnt-agency onboarding use cases. Kernel/admin orchestration on approve
 * is handled here; BFF proxies ERP approve and notifications only.
 */
@Service
@RequiredArgsConstructor
public class OnboardingService {

    private static final String STATUS_SUBMITTED = OnboardingApplication.ApplicationStatus.SUBMITTED.name();

    private final OnboardingApplicationR2dbcRepository onboardingRepo;
    private final AgencyRegistryService agencyRegistry;
    private final StaffMemberService staffMemberService;
    private final OnboardingKernelPort onboardingKernel;
    private final AdministrationCorePort administrationCore;

    @Transactional
    public Mono<SubmitResult> submit(SubmitInput input) {
        return onboardingRepo.findByApplicantUserIdAndTenantId(input.applicantUserId(), input.tenantId())
                .flatMap(existing -> Mono.<SubmitResult>error(new TntConflictException(
                        "Une demande d'inscription existe déjà pour ce compte.")))
                .switchIfEmpty(Mono.defer(() ->
                        agencyRegistry.register(new AgencyRegistryService.RegisterAgencyInput(
                                input.tenantId(), input.agencyName(), input.agencyCode(),
                                input.agencyType(), input.registrationNumber(),
                                input.addrStreet(), input.addrLandmark(), input.addrQuarter(),
                                input.addrCity(), input.addrRegion(), input.addrCountry(),
                                input.addrPostalCode(), input.addrLat(), input.addrLon(),
                                input.contactEmail(), input.contactPhone(), null, input.website()
                        )).map(AgencyOrgMapper::toAgencyResponse)
                        .flatMap(agency -> agencyRegistry.updateSettings(
                                        new AgencyRegistryService.UpdateSettingsInput(
                                                input.tenantId(), agency.id(),
                                                input.autoAssignMissions(),
                                                input.allowFreelancerAssociation(),
                                                input.hubRetentionDelayHours(),
                                                BigDecimal.ZERO,
                                                Math.max(1, input.maxActiveBranches()),
                                                "Africa/Douala"))
                                .then(Mono.defer(() -> {
                                    Instant now = Instant.now();
                                    OnboardingApplication application = OnboardingApplication.submit(
                                            UUID.randomUUID(), input.tenantId(), agency.id(),
                                            input.applicantUserId(), input.legalName(),
                                            input.ownerName(), input.ownerEmail(), input.ownerPhone(),
                                            input.ownerNationalId(), input.ownerIdType(),
                                            input.docCniKey(), input.docRccmKey(), input.docProofKey(),
                                            now);
                                    return onboardingRepo.save(OnboardingMapper.toEntity(application))
                                            .flatMap(saved -> tryCompleteKernelIdentity(
                                                    input.tenantId(),
                                                    input.applicantUserId(),
                                                    agency.id(),
                                                    saved.getId(),
                                                    agency.status()));
                                })))));
    }

    /**
     * Phase 1 — create Kernel BusinessActor (via Core) then persist, or persist a
     * pre-created actor id when {@code kernelBusinessActorId} is provided (legacy).
     */
    @Transactional
    public Mono<KernelIdentityResult> completeKernelIdentity(LinkKernelIdentityInput input) {
        return requireApplication(input.tenantId(), input.agencyId())
                .flatMap(app -> {
                    if (!app.getApplicantUserId().equals(input.applicantUserId())) {
                        return Mono.error(new TntUnauthorizedException(
                                "Seul le candidat propriétaire peut enregistrer l'identité Kernel."));
                    }
                    if (app.isKernelIdentityReady()) {
                        return Mono.just(new KernelIdentityResult(
                                app.getAgencyId(), app.getId(),
                                app.getKernelBusinessActorId(), true));
                    }
                    if (input.kernelBusinessActorId() != null) {
                        return persistKernelActor(app, input.tenantId(), input.kernelBusinessActorId());
                    }
                    return agencyRegistry.getById(input.tenantId(), input.agencyId())
                            .map(AgencyOrgMapper::toAgencyResponse)
                            .flatMap(agency -> onboardingKernel
                                    .onboardApplicantBusinessActor(agency, app)
                                    .flatMap(actorId -> persistKernelActor(app, input.tenantId(), actorId)))
                            .onErrorMap(e -> new IllegalStateException(
                                    "Échec identité Kernel candidat: " + e.getMessage()
                                            + " — vérifiez JWT candidat et credentials Core.", e));
                });
    }

    /** @deprecated use {@link #completeKernelIdentity(LinkKernelIdentityInput)} */
    @Transactional
    public Mono<KernelIdentityResult> linkKernelIdentity(LinkKernelIdentityInput input) {
        return completeKernelIdentity(input);
    }

    private Mono<SubmitResult> tryCompleteKernelIdentity(
            UUID tenantId, UUID applicantUserId, UUID agencyId,
            UUID applicationId, String agencyStatus) {
        return completeKernelIdentity(new LinkKernelIdentityInput(
                        tenantId, applicantUserId, agencyId, null))
                .map(k -> new SubmitResult(
                        agencyId, applicationId, agencyStatus,
                        k.kernelBusinessActorId(), k.readyForAdminApproval()))
                .onErrorResume(e -> Mono.just(new SubmitResult(
                        agencyId, applicationId, agencyStatus, null, false)));
    }

    private Mono<KernelIdentityResult> persistKernelActor(
            OnboardingApplication app, UUID tenantId, UUID actorId) {
        Instant now = Instant.now();
        app.linkKernelBusinessActor(actorId, now);
        return onboardingRepo.save(OnboardingMapper.toEntity(app))
                .then(agencyRegistry.linkKernelBusinessActor(tenantId, app.getAgencyId(), actorId))
                .thenReturn(new KernelIdentityResult(
                        app.getAgencyId(), app.getId(),
                        app.getKernelBusinessActorId(), true));
    }

    public Mono<MyOnboardingStatus> getForApplicant(UUID tenantId, UUID applicantUserId) {
        return onboardingRepo.findByApplicantUserIdAndTenantId(applicantUserId, tenantId)
                .switchIfEmpty(Mono.error(new TntNotFoundException(
                        "ONBOARDING_NOT_FOUND", "Aucune demande d'inscription trouvée pour ce compte.")))
                .flatMap(entity -> {
                    OnboardingApplication app = OnboardingMapper.toDomain(entity);
                    return agencyRegistry.getById(tenantId, app.getAgencyId())
                            .map(AgencyOrgMapper::toAgencyResponse)
                            .map(agency -> new MyOnboardingStatus(
                                    app.getId(), app.getAgencyId(), agency.name(),
                                    app.getApplicationStatus().name(), agency.status(),
                                    app.getKernelBusinessActorId(), app.isKernelIdentityReady()));
                });
    }

    public Flux<OnboardingListItemResponse> listPending(UUID tenantId) {
        return onboardingRepo.findPendingByTenantId(STATUS_SUBMITTED, tenantId)
                .flatMap(entity -> toListItem(tenantId, OnboardingMapper.toDomain(entity)));
    }

    public Mono<OnboardingDetailResponse> getByAgencyId(UUID tenantId, UUID agencyId) {
        return requireApplication(tenantId, agencyId)
                .flatMap(app -> agencyRegistry.getById(tenantId, agencyId)
                        .map(AgencyOrgMapper::toAgencyResponse)
                        .map(agency -> OnboardingMapper.toDetail(app, agency)));
    }

    @Transactional
    public Mono<AgencyRegistryResponse> approve(UUID tenantId, UUID agencyId, UUID reviewerId) {
        return requireApplication(tenantId, agencyId)
                .flatMap(app -> {
                    if (!app.isKernelIdentityReady()) {
                        return Mono.error(new TntValidationException(
                                "Identité Kernel candidat incomplète. "
                                        + "Le propriétaire doit compléter la phase 1 avant approbation."));
                    }
                    return agencyRegistry.getById(tenantId, agencyId)
                            .map(AgencyOrgMapper::toAgencyResponse)
                            .flatMap(agency -> agencyRegistry.getSettings(tenantId, agencyId)
                                    .map(AgencySettingsEntity::getDefaultCurrency)
                                    .defaultIfEmpty("XAF")
                                    .flatMap(currency -> onboardingKernel
                                            .provisionOrganization(new OnboardingKernelPort.ProvisionRequest(
                                                    agency, app, currency, reviewerId))
                                            .flatMap(kernel -> agencyRegistry.linkKernelIdentity(
                                                    tenantId, agencyId,
                                                    kernel.organizationId(),
                                                    kernel.businessActorId(),
                                                    kernel.coreAgencyId())
                                                    .map(AgencyOrgMapper::toAgencyResponse)
                                                    .map(saved -> new ApprovalContext(saved, kernel)))
                                            .switchIfEmpty(Mono.just(new ApprovalContext(agency, null)))
                                            .flatMap(ctx -> syncPlatformCoreIfNeeded(tenantId, ctx))
                                            .flatMap(ctx -> continueApproval(tenantId, app, ctx, reviewerId))));
                });
    }

    private Mono<ApprovalContext> syncPlatformCoreIfNeeded(UUID tenantId, ApprovalContext ctx) {
        AgencyRegistryResponse agency = ctx.agency();
        if (agency.kernelOrganizationId() == null || agency.coreAgencyId() != null) {
            return Mono.just(ctx);
        }
        return agencyRegistry.syncPlatformCore(tenantId, agency.id())
                .map(AgencyOrgMapper::toAgencyResponse)
                .map(synced -> new ApprovalContext(synced, ctx.kernel()));
    }

    private Mono<AgencyRegistryResponse> continueApproval(
            UUID tenantId,
            OnboardingApplication app,
            ApprovalContext ctx,
            UUID reviewerId) {
        AgencyRegistryResponse agency = ctx.agency();
        UUID organizationId = agency.kernelOrganizationId() != null
                ? agency.kernelOrganizationId()
                : tenantId;

        Mono<Void> provisionAdmin = provisionAdminViaCoreIfNeeded(
                tenantId, organizationId, reviewerId, ctx.kernel());

        Mono<Void> assignOwner = assignOwnerRoleIfLinked(agency, app, reviewerId, ctx.kernel());

        return provisionAdmin
                .then(assignOwner)
                .then(completeApproval(tenantId, app, agency.id(), reviewerId));
    }

    private Mono<AgencyRegistryResponse> completeApproval(
            UUID tenantId, OnboardingApplication app, UUID agencyId, UUID reviewerId) {
        Instant now = Instant.now();
        app.approve(reviewerId, now);
        return onboardingRepo.save(OnboardingMapper.toEntity(app))
                .then(agencyRegistry.activate(tenantId, agencyId))
                .map(AgencyOrgMapper::toAgencyResponse)
                .flatMap(agency -> staffMemberService.register(new StaffMemberService.RegisterInput(
                        tenantId, agencyId, null,
                        app.getOwnerName(), app.getOwnerPhone(), app.getOwnerEmail(),
                        StaffRole.AGENCY_MANAGER)).thenReturn(agency));
    }

    private Mono<Void> provisionAdminViaCoreIfNeeded(
            UUID tenantId,
            UUID organizationId,
            UUID reviewerId,
            OnboardingKernelPort.ProvisionResult kernelResult) {
        if (kernelResult != null
                && kernelResult.tntRolesProvisioned()
                && kernelResult.platformOptionsInitialized()) {
            return Mono.empty();
        }
        return administrationCore
                .provisionRoleTemplates(tenantId, organizationId, reviewerId)
                .then(administrationCore.initializePlatformOptions(tenantId));
    }

    private Mono<Void> assignOwnerRoleIfLinked(
            AgencyRegistryResponse agency,
            OnboardingApplication app,
            UUID reviewerId,
            OnboardingKernelPort.ProvisionResult kernelResult) {
        if (agency.kernelOrganizationId() == null) {
            return Mono.empty();
        }
        if (kernelResult != null && kernelResult.ownerRoleAssignmentId() != null) {
            return Mono.empty();
        }
        return onboardingKernel.assignAgencyManagerRole(
                agency.id(),
                agency.tenantId(),
                agency.kernelOrganizationId(),
                app.getApplicantUserId(),
                reviewerId);
    }

    private record ApprovalContext(
            AgencyRegistryResponse agency,
            OnboardingKernelPort.ProvisionResult kernel) {}

    @Transactional
    public Mono<OnboardingListItemResponse> reject(
            UUID tenantId, UUID agencyId, UUID reviewerId, String reason) {
        return requireApplication(tenantId, agencyId)
                .flatMap(app -> {
                    Instant now = Instant.now();
                    app.reject(reason, reviewerId, now);
                    return onboardingRepo.save(OnboardingMapper.toEntity(app))
                            .then(agencyRegistry.reject(tenantId, agencyId))
                            .then(toListItem(tenantId, app));
                });
    }

    private Mono<OnboardingListItemResponse> toListItem(UUID tenantId, OnboardingApplication app) {
        return agencyRegistry.getById(tenantId, app.getAgencyId())
                .map(AgencyOrgMapper::toAgencyResponse)
                .map(agency -> OnboardingMapper.toListItem(app, agency));
    }

    private Mono<OnboardingApplication> requireApplication(UUID tenantId, UUID agencyId) {
        return onboardingRepo.findByAgencyIdAndTenantId(agencyId, tenantId)
                .map(OnboardingMapper::toDomain)
                .switchIfEmpty(Mono.error(new TntNotFoundException(
                        "ONBOARDING_NOT_FOUND",
                        "Demande d'inscription introuvable: " + agencyId)));
    }

    public record SubmitInput(
            UUID tenantId, UUID applicantUserId,
            String agencyName, String legalName, String agencyCode, String agencyType,
            String registrationNumber,
            String addrStreet, String addrLandmark, String addrQuarter,
            String addrCity, String addrRegion, String addrCountry, String addrPostalCode,
            Double addrLat, Double addrLon,
            String contactEmail, String contactPhone, String website,
            String ownerName, String ownerEmail, String ownerPhone,
            String ownerNationalId, String ownerIdType,
            String docCniKey, String docRccmKey, String docProofKey,
            Boolean autoAssignMissions, Boolean allowFreelancerAssociation,
            Integer hubRetentionDelayHours, Integer maxActiveBranches) {}

    public record SubmitResult(
            UUID agencyId, UUID applicationId, String agencyStatus,
            UUID kernelBusinessActorId, boolean kernelIdentityReady) {}

    public record LinkKernelIdentityInput(
            UUID tenantId, UUID applicantUserId, UUID agencyId, UUID kernelBusinessActorId) {}

    public record KernelIdentityResult(
            UUID agencyId, UUID applicationId,
            UUID kernelBusinessActorId, boolean readyForAdminApproval) {}

    public record MyOnboardingStatus(
            UUID applicationId, UUID agencyId, String agencyName,
            String applicationStatus, String agencyStatus,
            UUID kernelBusinessActorId, boolean kernelIdentityReady) {}
}
