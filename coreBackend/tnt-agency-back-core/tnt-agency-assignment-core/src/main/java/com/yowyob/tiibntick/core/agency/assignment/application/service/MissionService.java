package com.yowyob.tiibntick.core.agency.assignment.application.service;



import com.yowyob.tiibntick.common.exception.TntNotFoundException;

import com.yowyob.tiibntick.common.exception.TntValidationException;

import com.yowyob.tiibntick.core.agency.assignment.adapter.out.clients.DeliveryMissionPort;

import com.yowyob.tiibntick.core.agency.assignment.adapter.out.persistence.AgencyMissionR2dbcRepository;

import com.yowyob.tiibntick.core.agency.assignment.application.mapper.MissionMapper;

import com.yowyob.tiibntick.core.agency.assignment.domain.AgencyMission;

import com.yowyob.tiibntick.core.agency.org.hubops.domain.support.TrackingCodeGenerator;

import com.yowyob.tiibntick.core.agency.assignment.domain.vo.MissionStatus;

import com.yowyob.tiibntick.core.agency.commission.application.service.CommissionService;

import com.yowyob.tiibntick.core.agency.eventing.application.port.AgencyEventPublisher;

import com.yowyob.tiibntick.core.agency.eventing.domain.event.DeliveryCompleted;

import com.yowyob.tiibntick.core.agency.eventing.domain.event.MissionAssigned;

import com.yowyob.tiibntick.core.agency.eventing.domain.event.MissionCancelled;

import com.yowyob.tiibntick.core.agency.eventing.domain.event.MissionCreated;

import com.yowyob.tiibntick.core.agency.eventing.domain.event.MissionStarted;

import com.yowyob.tiibntick.core.agency.compliance.application.service.ComplianceOrchestrator;

import com.yowyob.tiibntick.core.agency.org.adapter.out.persistence.AgencyRelayHubR2dbcRepository;

import com.yowyob.tiibntick.core.agency.org.application.service.AgencyRegistryService;

import com.yowyob.tiibntick.core.agency.org.adapter.out.clients.TrustPort;
import com.yowyob.tiibntick.core.agency.org.hubops.application.service.HubParcelService;

import lombok.RequiredArgsConstructor;

import org.slf4j.Logger;

import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;

import reactor.core.publisher.Flux;

import reactor.core.publisher.Mono;



import java.math.BigDecimal;

import java.time.Instant;

import java.util.UUID;



@Service

@RequiredArgsConstructor

public class MissionService {



    private static final Logger log = LoggerFactory.getLogger(MissionService.class);

    private static final double DEFAULT_DISTANCE_KM = 10.0;

    private static final BigDecimal DEFAULT_COMMISSION = BigDecimal.valueOf(5000);



    private final AgencyMissionR2dbcRepository missionRepo;

    private final AgencyRegistryService agencyRegistry;

    private final DeliveryMissionPort deliveryMission;

    private final MissionDeliveryOrchestrator deliveryOrchestrator;

    private final AgencyRelayHubR2dbcRepository hubRepo;

    private final HubParcelService hubParcelService;

    private final TrustPort trust;

    private final CommissionService commissionService;

    private final AgencyEventPublisher eventPublisher;

    private final ComplianceOrchestrator complianceOrchestrator;



    public record CreatedMissionResult(AgencyMission mission, String trackingCode) {}



    public record CreateFromIntakeInput(

            UUID tenantId, UUID agencyId, UUID branchId,

            String pickupAddress, String deliveryAddress,

            String senderName, String recipientName, String recipientPhone,

            Double weightKg, int packagesCount, UUID targetHubId) {}



    public record CreateMissionInput(

            UUID tenantId, UUID agencyId, UUID branchId, UUID coreMissionId,

            Instant scheduledAt, String pickupAddress, String deliveryAddress,

            String senderName, String recipientName, String recipientPhone,

            Double weightKg, Double distanceKm, Integer packagesCount,

            String priority, UUID targetHubId) {}



    @Transactional

    public Mono<AgencyMission> create(CreateMissionInput input) {

        Instant now = input.scheduledAt() != null ? input.scheduledAt() : Instant.now();

        if (input.coreMissionId() != null) {

            UUID missionId = UUID.randomUUID();

            AgencyMission mission = AgencyMission.create(

                    missionId, input.tenantId(), input.agencyId(),

                    input.coreMissionId(), now, Instant.now());

            mission.applyCreationSnapshot(

                    input.branchId(), input.pickupAddress(), input.deliveryAddress(),

                    input.senderName(), input.recipientName(), input.recipientPhone(),

                    input.weightKg(), input.distanceKm() != null ? input.distanceKm() : DEFAULT_DISTANCE_KM,

                    input.packagesCount() != null ? input.packagesCount() : 1,

                    input.priority() != null ? input.priority() : "NORMAL",

                    input.targetHubId(), Instant.now());

            return persist(mission).flatMap(this::publishCreated);

        }

        return createFromIntake(new CreateFromIntakeInput(

                input.tenantId(), input.agencyId(), input.branchId(),

                input.pickupAddress(), input.deliveryAddress(),

                input.senderName(), input.recipientName(), input.recipientPhone(),

                input.weightKg(), input.packagesCount() != null ? input.packagesCount() : 1,

                input.targetHubId()

        )).map(CreatedMissionResult::mission);

    }



    @Transactional

    public Mono<CreatedMissionResult> createFromIntake(CreateFromIntakeInput input) {

        Instant now = Instant.now();

        UUID agencyMissionId = UUID.randomUUID();

        String trackingCode = TrackingCodeGenerator.generate();



        return agencyRegistry.getById(input.tenantId(), input.agencyId())

                .flatMap(agency -> {

                    if (agency.getCoreAgencyId() == null || agency.getKernelOrganizationId() == null) {

                        return Mono.error(new TntValidationException(

                                "Agence non synchronisée avec le Core (coreAgencyId / kernelOrganizationId)."));

                    }

                    return deliveryMission.createMission(new DeliveryMissionPort.CreateMissionRequest(

                            input.tenantId(),

                            agency.getKernelOrganizationId(),

                            input.agencyId(),

                            agency.getCoreAgencyId(),

                            agencyMissionId,

                            input.pickupAddress(),

                            input.deliveryAddress(),

                            input.senderName(),

                            input.recipientName(),

                            input.recipientPhone(),

                            input.weightKg(),

                            now

                    )).map(core -> {

                        AgencyMission mission = AgencyMission.create(

                                agencyMissionId, input.tenantId(), input.agencyId(),

                                core.coreMissionId(), now, now);

                        mission.applyCreationSnapshot(

                                input.branchId(),

                                input.pickupAddress(),

                                input.deliveryAddress(),

                                input.senderName(),

                                input.recipientName(),

                                input.recipientPhone(),

                                input.weightKg(),

                                DEFAULT_DISTANCE_KM,

                                input.packagesCount(),

                                "NORMAL",

                                input.targetHubId(),

                                now);

                        return mission;

                    });

                })

                .flatMap(mission -> missionRepo.save(MissionMapper.toEntity(mission))

                        .map(MissionMapper::toDomain)

                        .flatMap(this::publishCreated)

                        .map(saved -> new CreatedMissionResult(saved, trackingCode)));

    }



    @Transactional

    public Mono<AgencyMission> assign(UUID tenantId, UUID missionId, UUID delivererId, UUID vehicleId) {

        Instant now = Instant.now();

        UUID resolvedVehicle = vehicleId != null ? vehicleId : delivererId;

        return requireMission(missionId, tenantId)

                .flatMap(mission -> {

                    mission.assign(delivererId, resolvedVehicle, now);

                    return persist(mission).flatMap(this::publishAssigned);

                });

    }



    @Transactional

    public Mono<AgencyMission> reassign(UUID tenantId, UUID missionId, UUID delivererId, UUID vehicleId) {

        Instant now = Instant.now();

        UUID resolvedVehicle = vehicleId != null ? vehicleId : delivererId;

        return requireMission(missionId, tenantId)

                .flatMap(mission -> {

                    mission.reassign(delivererId, resolvedVehicle, now);

                    return persist(mission);

                });

    }



    @Transactional

    public Mono<AgencyMission> cancel(UUID tenantId, UUID missionId, String reason) {

        Instant now = Instant.now();

        return requireMission(missionId, tenantId)

                .flatMap(mission -> deliveryOrchestrator.cancelOnCore(mission, tenantId, reason, now))

                .flatMap(this::persist)

                .flatMap(this::publishCancelled);

    }



    @Transactional

    public Mono<AgencyMission> reschedule(UUID tenantId, UUID missionId, Instant newScheduledAt) {

        Instant now = Instant.now();

        return requireMission(missionId, tenantId)

                .flatMap(mission -> {

                    mission.reschedule(newScheduledAt, now);

                    return persist(mission);

                });

    }



    @Transactional

    public Mono<AgencyMission> pickup(UUID tenantId, UUID missionId, UUID delivererId) {

        Instant now = Instant.now();

        return requireMission(missionId, tenantId)

                .flatMap(mission -> deliveryOrchestrator.startOnCore(mission, tenantId, now))

                .flatMap(this::persist)

                .flatMap(this::publishStarted)

                .flatMap(saved -> trust.recordPickup(new TrustPort.PickupTransaction(

                        saved.getCoreMissionId() != null ? saved.getCoreMissionId() : saved.getId(),

                        saved.getId(),

                        delivererId,

                        0.0,

                        0.0,

                        now)).thenReturn(saved));

    }



    @Transactional

    public Mono<AgencyMission> deliver(UUID tenantId, UUID missionId, UUID delivererId, String proofReference) {

        Instant now = Instant.now();

        return requireMission(missionId, tenantId)

                .flatMap(mission -> deliveryOrchestrator.completeOnCore(mission, tenantId, proofReference, now))

                .flatMap(this::persist)

                .flatMap(saved -> createAutoCommission(saved, delivererId).thenReturn(saved))

                .flatMap(saved -> publishDelivered(saved, delivererId, proofReference))

                .flatMap(saved -> trust.recordDelivery(new TrustPort.DeliveryTransaction(

                        saved.getId(),

                        delivererId,

                        "",

                        proofReference != null ? proofReference : "",

                        "",

                        0.0,

                        0.0,

                        now)).thenReturn(saved));

    }



    @Transactional

    public Mono<AgencyMission> depositAtHub(

            UUID tenantId, UUID missionId, UUID hubId, UUID delivererId, String trackingCode) {

        Instant now = Instant.now();

        String code = trackingCode != null ? trackingCode : TrackingCodeGenerator.generate();

        return requireMission(missionId, tenantId)

                .flatMap(mission -> hubRepo.findByIdAndTenantId(hubId, tenantId)

                        .switchIfEmpty(Mono.error(new TntNotFoundException(

                                "HUB_NOT_FOUND", "Hub introuvable: " + hubId)))

                        .flatMap(hub -> deliveryOrchestrator.depositAtHubOnCore(

                                mission, tenantId, hub.getCoreHubId(), now))

                        .flatMap(this::persist)

                        .flatMap(saved -> hubParcelService.deposit(new HubParcelService.DepositInput(

                                tenantId, hubId, missionId, code, null)).thenReturn(saved)));

    }



    @Transactional

    public Mono<AgencyMission> reportAnomaly(

            UUID tenantId, UUID missionId, UUID delivererId,

            String anomalyType, String description, boolean fatal) {

        Instant now = Instant.now();

        return requireMission(missionId, tenantId)

                .flatMap(mission -> {

                    log.warn("[ANOMALY] mission={} type={} deliverer={} desc={}",

                            missionId, anomalyType, delivererId, description);

                    return complianceOrchestrator.reportIncident(

                                    tenantId, mission.getAgencyId(), missionId,

                                    delivererId, anomalyType, description)

                            .then(Mono.defer(() -> {

                                if (!fatal) {

                                    return Mono.just(mission);

                                }

                                String reason = anomalyType + ": " + description;

                                return deliveryOrchestrator.failOnCore(mission, tenantId, reason, now)

                                        .flatMap(this::persist);

                            }));

                });
    }



    @Transactional
    public Mono<AgencyMission> syncFromCoreStatus(UUID tenantId, UUID coreMissionId, String coreStatus) {
        return syncFromCoreStatus(tenantId, coreMissionId, coreStatus, CoreMissionSyncHint.empty());
    }

    /**
     * Syncs Core delivery status onto the local agency projection.
     * If the projection is missing and {@code hint.agencyId()} is present, creates it first
     * (Kafka create/status events that arrive before a local mission row).
     */
    @Transactional
    public Mono<AgencyMission> syncFromCoreStatus(UUID tenantId, UUID coreMissionId, String coreStatus,
                                                  CoreMissionSyncHint hint) {
        Instant now = Instant.now();
        return missionRepo.findByCoreMissionIdAndTenantId(coreMissionId, tenantId)
                .map(MissionMapper::toDomain)
                .switchIfEmpty(Mono.defer(() -> createProjectionFromCoreHint(
                        tenantId, coreMissionId, coreStatus, hint, now)))
                .flatMap(mission -> applySyncedStatus(mission, coreStatus, now));
    }

    private Mono<AgencyMission> createProjectionFromCoreHint(
            UUID tenantId, UUID coreMissionId, String coreStatus,
            CoreMissionSyncHint hint, Instant now) {
        UUID agencyId = hint != null ? hint.agencyId() : null;
        if (agencyId == null) {
            log.warn("[Mission] skip create-on-Kafka: no agencyId for coreMissionId={} status={}",
                    coreMissionId, coreStatus);
            return Mono.empty();
        }
        AgencyMission mission = AgencyMission.create(
                UUID.randomUUID(), tenantId, agencyId, coreMissionId, now, now);
        Double distance = hint.distanceKm() != null ? hint.distanceKm() : DEFAULT_DISTANCE_KM;
        Double weight = hint.weightKg() != null ? hint.weightKg() : 1.0;
        mission.applyCreationSnapshot(
                hint.branchId(),
                hint.pickupAddress(),
                hint.deliveryAddress(),
                hint.senderName(),
                hint.recipientName(),
                hint.recipientPhone(),
                weight,
                distance,
                hint.packagesCount() != null ? hint.packagesCount() : 1,
                hint.priority() != null ? hint.priority() : "NORMAL",
                hint.targetHubId(),
                now);
        log.info("[Mission] created local projection from Kafka coreMissionId={} agencyId={}",
                coreMissionId, agencyId);
        return Mono.just(mission);
    }

    private Mono<AgencyMission> applySyncedStatus(AgencyMission mission, String coreStatus, Instant now) {
        MissionStatus mapped = DeliveryStatusSyncMapper.toAgencyStatus(coreStatus);
        if (mapped == MissionStatus.CANCELLED) {
            mission.cancel("Status synced from Core", now);
        } else if (mapped == MissionStatus.FAILED) {
            mission.fail("Status synced from Core", now);
        } else if (mapped == MissionStatus.DELIVERED) {
            mission.syncDeliveredFromCore(now);
        } else {
            DeliveryStatusSyncMapper.applyCoreStatus(mission, coreStatus, now);
        }
        return persist(mission);
    }

    /** Optional fields from Core Kafka payloads when projecting a missing local mission. */
    public record CoreMissionSyncHint(
            UUID agencyId,
            UUID branchId,
            String pickupAddress,
            String deliveryAddress,
            String senderName,
            String recipientName,
            String recipientPhone,
            Double weightKg,
            Double distanceKm,
            Integer packagesCount,
            String priority,
            UUID targetHubId
    ) {
        public static CoreMissionSyncHint empty() {
            return new CoreMissionSyncHint(
                    null, null, null, null, null, null, null, null, null, null, null, null);
        }

        public static CoreMissionSyncHint ofAgency(UUID agencyId) {
            return new CoreMissionSyncHint(
                    agencyId, null, null, null, null, null, null, null, null, null, null, null);
        }
    }

    public Mono<AgencyMission> getById(UUID tenantId, UUID missionId) {

        return requireMission(missionId, tenantId);

    }



    public Flux<AgencyMission> listByAgency(UUID tenantId, UUID agencyId) {

        return agencyRegistry.getById(tenantId, agencyId)

                .thenMany(missionRepo.findByAgencyIdAndTenantId(agencyId, tenantId))

                .map(MissionMapper::toDomain);

    }



    public Flux<AgencyMission> listByAgencyAndStatus(UUID tenantId, UUID agencyId, String status) {

        return missionRepo.findByAgencyIdAndTenantIdAndStatus(agencyId, tenantId, status)

                .map(MissionMapper::toDomain);

    }



    public Flux<AgencyMission> listByDeliverer(UUID tenantId, UUID delivererId) {

        return missionRepo.findByAssignedDelivererIdAndTenantId(delivererId, tenantId)

                .map(MissionMapper::toDomain);

    }



    private Mono<AgencyMission> persist(AgencyMission mission) {

        return missionRepo.save(MissionMapper.toEntity(mission)).map(MissionMapper::toDomain);

    }



    private Mono<AgencyMission> requireMission(UUID missionId, UUID tenantId) {

        return missionRepo.findByIdAndTenantId(missionId, tenantId)

                .map(MissionMapper::toDomain)

                .switchIfEmpty(Mono.error(new TntNotFoundException(

                        "MISSION_NOT_FOUND", "Mission introuvable: " + missionId)));

    }



    private Mono<Void> createAutoCommission(AgencyMission mission, UUID delivererId) {

        if (delivererId == null) {

            return Mono.empty();

        }

        return commissionService.create(new CommissionService.CreateInput(

                mission.getTenantId(), mission.getAgencyId(), delivererId,

                mission.getId(), DEFAULT_COMMISSION, "XAF"

        )).then();

    }



    private Mono<AgencyMission> publishCreated(AgencyMission m) {

        return eventPublisher.publish(new MissionCreated(

                UUID.randomUUID(), m.getId(), m.getTenantId(), m.getAgencyId(),

                m.getCoreMissionId(), m.getScheduledAt(), Instant.now())).thenReturn(m);

    }



    private Mono<AgencyMission> publishAssigned(AgencyMission m) {

        return eventPublisher.publish(new MissionAssigned(

                UUID.randomUUID(), m.getId(), m.getTenantId(), m.getAgencyId(),

                m.getCoreMissionId(), m.getAssignedDelivererId(), m.getAssignedVehicleId(),

                Instant.now())).thenReturn(m);

    }



    private Mono<AgencyMission> publishStarted(AgencyMission m) {

        return eventPublisher.publish(new MissionStarted(

                UUID.randomUUID(), m.getId(), m.getTenantId(), m.getAgencyId(),

                m.getCoreMissionId(), m.getAssignedDelivererId(), m.getStartedAt(),

                Instant.now())).thenReturn(m);

    }



    private Mono<AgencyMission> publishCancelled(AgencyMission m) {

        return eventPublisher.publish(new MissionCancelled(

                UUID.randomUUID(), m.getId(), m.getTenantId(), m.getAgencyId(),

                m.getCoreMissionId(), m.getCancellationReason(), m.getCancelledAt(),

                Instant.now())).thenReturn(m);

    }



    private Mono<AgencyMission> publishDelivered(AgencyMission m, UUID delivererId, String proofReference) {

        UUID resolvedDeliverer = delivererId != null ? delivererId : m.getAssignedDelivererId();

        return eventPublisher.publish(new DeliveryCompleted(

                UUID.randomUUID(), m.getId(), m.getTenantId(), m.getAgencyId(),

                resolvedDeliverer, proofReference, m.getCompletedAt(), Instant.now())).thenReturn(m);

    }

}

