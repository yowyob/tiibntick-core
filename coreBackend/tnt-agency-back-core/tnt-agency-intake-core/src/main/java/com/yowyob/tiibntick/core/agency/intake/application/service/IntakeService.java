package com.yowyob.tiibntick.core.agency.intake.application.service;

import com.yowyob.tiibntick.common.exception.TntNotFoundException;
import com.yowyob.tiibntick.common.exception.TntValidationException;
import com.yowyob.tiibntick.core.agency.assignment.application.service.MissionService;
import com.yowyob.tiibntick.core.agency.assignment.domain.AgencyMission;
import com.yowyob.tiibntick.core.agency.intake.adapter.in.web.dto.IntakeContextResponse;
import com.yowyob.tiibntick.core.agency.intake.adapter.in.web.dto.IntakeStatusResponse;
import com.yowyob.tiibntick.core.agency.intake.adapter.out.persistence.ClientIntakeRequestR2dbcRepository;
import com.yowyob.tiibntick.core.agency.intake.application.mapper.IntakeMapper;
import com.yowyob.tiibntick.core.agency.intake.domain.ClientIntakeRequest;
import com.yowyob.tiibntick.core.agency.intake.domain.ClientIntakeRequest.DeliveryMode;
import com.yowyob.tiibntick.core.agency.intake.domain.ClientIntakeRequest.Source;
import com.yowyob.tiibntick.core.agency.intake.domain.ClientIntakeRequest.Status;
import com.yowyob.tiibntick.core.agency.intake.domain.IntakeReferenceGenerator;
import com.yowyob.tiibntick.core.agency.org.adapter.in.web.dto.AgencyBranchResponse;
import com.yowyob.tiibntick.core.agency.org.application.service.AgencyBranchService;
import com.yowyob.tiibntick.core.agency.org.application.service.AgencyRegistryService;
import com.yowyob.tiibntick.core.agency.org.hubops.application.service.HubParcelService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Client intake queue — approve orchestrates mission creation (assignment-core)
 * and hub deposit (org hubops → inventory-core).
 */
@Service
@RequiredArgsConstructor
public class IntakeService {

    private final ClientIntakeRequestR2dbcRepository intakeRepo;
    private final AgencyRegistryService agencyRegistry;
    private final AgencyBranchService branchService;
    private final MissionService missionService;
    private final HubParcelService hubParcelService;

    public Mono<IntakeContextResponse> getContext(UUID tenantId, UUID agencyId, UUID branchId) {
        return agencyRegistry.getById(tenantId, agencyId)
                .zipWith(branchService.getById(tenantId, branchId))
                .map(t -> new IntakeContextResponse(
                        t.getT1().getId(), t.getT1().getName(),
                        t.getT2().id(), t.getT2().name(),
                        formatBranchAddress(t.getT2())));
    }

    @Transactional
    public Mono<ClientIntakeRequest> submit(SubmitInput input) {
        if (input.deliveryMode() == DeliveryMode.HUB && input.targetHubId() == null) {
            return Mono.error(new TntValidationException(
                    "Un point relais est requis pour une livraison via hub."));
        }
        return agencyRegistry.getById(input.tenantId(), input.agencyId())
                .then(branchService.getById(input.tenantId(), input.branchId()))
                .then(Mono.defer(() -> {
                    Instant now = Instant.now();
                    ClientIntakeRequest request = ClientIntakeRequest.submit(
                            UUID.randomUUID(), input.tenantId(), input.agencyId(), input.branchId(),
                            IntakeReferenceGenerator.generate(), input.source(),
                            input.senderName(), input.senderPhone(),
                            input.recipientName(), input.recipientPhone(),
                            input.pickupAddress(), input.deliveryAddress(),
                            input.weightKg(), input.packagesCount(),
                            input.deliveryMode(), input.targetHubId(), input.notes(), now);
                    return intakeRepo.save(IntakeMapper.toEntity(request))
                            .map(IntakeMapper::toDomain);
                }));
    }

    @Transactional
    public Mono<ClientIntakeRequest> submitAndApproveWalkIn(WalkInApproveInput input) {
        return submit(new SubmitInput(
                input.tenantId(), input.agencyId(), input.branchId(), Source.WALK_IN,
                input.senderName(), input.senderPhone(),
                input.recipientName(), input.recipientPhone(),
                input.pickupAddress(), input.deliveryAddress(),
                input.weightKg(), input.packagesCount(),
                input.deliveryMode(), input.targetHubId(), input.notes()
        )).flatMap(saved -> approve(new ApproveInput(
                saved.getId(), input.tenantId(), input.reviewerId(),
                input.delivererId(), input.vehicleId(),
                input.deliveryModeOverride(), input.targetHubIdOverride())));
    }

    public Flux<IntakeStatusResponse> listPending(UUID tenantId, UUID agencyId) {
        return intakeRepo.findByAgencyIdAndTenantIdAndStatusOrderByCreatedAtDesc(
                        agencyId, tenantId, Status.SUBMITTED.name())
                .map(IntakeMapper::toDomain)
                .flatMap(this::enrichStatus);
    }

    public Mono<IntakeStatusResponse> findByReference(String referenceCode) {
        return intakeRepo.findByReferenceCode(referenceCode)
                .map(IntakeMapper::toDomain)
                .switchIfEmpty(Mono.error(new TntNotFoundException(
                        "INTAKE_NOT_FOUND", "Demande introuvable: " + referenceCode)))
                .flatMap(this::enrichStatus);
    }

    @Transactional
    public Mono<ClientIntakeRequest> approve(ApproveInput input) {
        return requireIntake(input.intakeId(), input.tenantId())
                .flatMap(intake -> {
                    if (intake.getStatus() != Status.SUBMITTED) {
                        return Mono.error(new TntValidationException(
                                "Seules les demandes en attente peuvent être approuvées."));
                    }
                    DeliveryMode mode = input.deliveryModeOverride() != null
                            ? input.deliveryModeOverride() : intake.getDeliveryMode();
                    UUID hubId = input.targetHubIdOverride() != null
                            ? input.targetHubIdOverride() : intake.getTargetHubId();

                    return missionService.createFromIntake(new MissionService.CreateFromIntakeInput(
                                    intake.getTenantId(), intake.getAgencyId(), intake.getBranchId(),
                                    intake.getPickupAddress(), intake.getDeliveryAddress(),
                                    intake.getSenderName(), intake.getRecipientName(),
                                    intake.getRecipientPhone(), intake.getWeightKg(),
                                    intake.getPackagesCount(), hubId))
                            .flatMap(created -> assignIfNeeded(created, input))
                            .flatMap(pair -> depositIfHub(pair.mission(), pair.trackingCode(),
                                    intake, mode, hubId))
                            .flatMap(pair -> {
                                intake.approve(pair.mission().getId(), pair.trackingCode(),
                                        input.reviewerId(), Instant.now());
                                return intakeRepo.save(IntakeMapper.toEntity(intake))
                                        .map(IntakeMapper::toDomain);
                            });
                });
    }

    @Transactional
    public Mono<ClientIntakeRequest> reject(RejectInput input) {
        return requireIntake(input.intakeId(), input.tenantId())
                .flatMap(intake -> {
                    if (intake.getStatus() != Status.SUBMITTED) {
                        return Mono.error(new TntValidationException("Demande déjà traitée."));
                    }
                    intake.reject(input.reason(), input.reviewerId(), Instant.now());
                    return intakeRepo.save(IntakeMapper.toEntity(intake))
                            .map(IntakeMapper::toDomain);
                });
    }

    public Mono<IntakeStatusResponse> enrichStatus(ClientIntakeRequest intake) {
        return getContext(intake.getTenantId(), intake.getAgencyId(), intake.getBranchId())
                .map(ctx -> IntakeStatusResponse.from(intake, ctx.agencyName(), ctx.branchName()));
    }

    private Mono<MissionTrackingPair> assignIfNeeded(
            MissionService.CreatedMissionResult created, ApproveInput input) {
        if (input.delivererId() == null) {
            return Mono.just(new MissionTrackingPair(created.mission(), created.trackingCode()));
        }
        return missionService.assign(
                        created.mission().getTenantId(),
                        created.mission().getId(),
                        input.delivererId(),
                        input.vehicleId())
                .map(m -> new MissionTrackingPair(m, created.trackingCode()));
    }

    private Mono<MissionTrackingPair> depositIfHub(
            AgencyMission mission, String trackingCode,
            ClientIntakeRequest intake, DeliveryMode mode, UUID hubId) {
        if (mode != DeliveryMode.HUB || hubId == null) {
            return Mono.just(new MissionTrackingPair(mission, trackingCode));
        }
        return hubParcelService.deposit(new HubParcelService.DepositInput(
                        intake.getTenantId(), hubId, mission.getId(), trackingCode, null))
                .thenReturn(new MissionTrackingPair(mission, trackingCode));
    }

    private Mono<ClientIntakeRequest> requireIntake(UUID intakeId, UUID tenantId) {
        return intakeRepo.findByIdAndTenantId(intakeId, tenantId)
                .map(IntakeMapper::toDomain)
                .switchIfEmpty(Mono.error(new TntNotFoundException(
                        "INTAKE_NOT_FOUND", "Demande introuvable: " + intakeId)));
    }

    private static String formatBranchAddress(AgencyBranchResponse branch) {
        if (branch.address() == null) {
            return null;
        }
        AgencyBranchResponse.AddressResponse a = branch.address();
        return Stream.of(a.street(), a.landmark(), a.quarter(), a.city(), a.region(), a.country())
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.joining(", "));
    }

    private record MissionTrackingPair(AgencyMission mission, String trackingCode) {}

    public record SubmitInput(
            UUID tenantId, UUID agencyId, UUID branchId, Source source,
            String senderName, String senderPhone,
            String recipientName, String recipientPhone,
            String pickupAddress, String deliveryAddress,
            Double weightKg, int packagesCount,
            DeliveryMode deliveryMode, UUID targetHubId, String notes) {}

    public record ApproveInput(
            UUID intakeId, UUID tenantId, UUID reviewerId,
            UUID delivererId, UUID vehicleId,
            DeliveryMode deliveryModeOverride, UUID targetHubIdOverride) {}

    public record RejectInput(UUID intakeId, UUID tenantId, UUID reviewerId, String reason) {}

    public record WalkInApproveInput(
            UUID tenantId, UUID agencyId, UUID branchId, UUID reviewerId,
            String senderName, String senderPhone,
            String recipientName, String recipientPhone,
            String pickupAddress, String deliveryAddress,
            Double weightKg, int packagesCount,
            DeliveryMode deliveryMode, UUID targetHubId, String notes,
            UUID delivererId, UUID vehicleId,
            DeliveryMode deliveryModeOverride, UUID targetHubIdOverride) {}
}
