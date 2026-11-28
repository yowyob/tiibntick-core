package com.yowyob.tiibntick.core.resource.application.service;

import com.yowyob.tiibntick.core.resource.application.port.in.*;
import com.yowyob.tiibntick.core.resource.application.port.out.FreelancerEquipmentRepository;
import com.yowyob.tiibntick.core.resource.application.port.out.FreelancerVehicleRepository;
import com.yowyob.tiibntick.core.resource.application.port.out.ResourceEventPublisherPort;
import com.yowyob.tiibntick.core.resource.domain.event.FreelancerVehicleAssignedToMissionEvent;
import com.yowyob.tiibntick.core.resource.domain.event.FreelancerVehicleRegisteredEvent;
import com.yowyob.tiibntick.core.resource.domain.event.FreelancerVehicleReleasedFromMissionEvent;
import com.yowyob.tiibntick.core.resource.domain.exception.FreelancerFleetCapacityExceededException;
import com.yowyob.tiibntick.core.resource.domain.exception.FreelancerVehicleNotFoundException;
import com.yowyob.tiibntick.core.resource.domain.model.EquipmentType;
import com.yowyob.tiibntick.core.resource.domain.model.FreelancerEquipment;
import com.yowyob.tiibntick.core.resource.domain.model.FreelancerVehicle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Application service orchestrating the FreelancerOrganization fleet lifecycle.
 *
 * <p>Handles all operations on {@code FreelancerVehicle} and {@code FreelancerEquipment}:
 * registration, mission assignment, release, deactivation, and fleet queries.
 *
 * <p><b>Kernel integration principle:</b> The service stores {@code ownerOrgId} (UUID from
 * {@code tnt-organization-core}) as a pure integration key. There is no injection of
 * {@code tnt-organization-core} beans — the existence of the FreelancerOrg is assumed valid
 * when this service is called (validated upstream by the API Gateway or tnt-organization-core
 * event flow).
 *
 * <p><b>Fleet capacity enforcement:</b> A FreelancerOrg can have at most
 * {@code FreelancerVehicle.MAX_VEHICLES_PER_ORG} (3) active vehicles. This is checked
 * reactively before each new vehicle registration.
 *
 * @author MANFOUO Braun
 */
@Service
public class FreelancerFleetApplicationService implements
        AddFreelancerVehicleUseCase,
        AddFreelancerEquipmentUseCase,
        AssignFreelancerVehicleToMissionUseCase,
        ReleaseFreelancerVehicleFromMissionUseCase,
        ListFreelancerFleetUseCase,
        GetFreelancerVehicleUseCase,
        DeactivateFreelancerVehicleUseCase,
        AddPersonalVehicleUseCase,
        UpdateFreelancerVehicleUseCase {

    private static final Logger log = LoggerFactory.getLogger(FreelancerFleetApplicationService.class);

    private final FreelancerVehicleRepository vehicleRepository;
    private final FreelancerEquipmentRepository equipmentRepository;
    private final ResourceEventPublisherPort eventPublisher;

    public FreelancerFleetApplicationService(FreelancerVehicleRepository vehicleRepository,
            FreelancerEquipmentRepository equipmentRepository,
            ResourceEventPublisherPort eventPublisher) {
        this.vehicleRepository = vehicleRepository;
        this.equipmentRepository = equipmentRepository;
        this.eventPublisher = eventPublisher;
    }

    // ── AddFreelancerVehicleUseCase ───────────────────────────────────────

    /**
     * {@inheritDoc}
     *
     * <p>Enforces the 3-vehicle fleet cap per FreelancerOrg before saving.
     * Checks for duplicate plate number within the same org.
     */
    @Transactional
    @Override
    public Mono<FreelancerVehicle> addVehicle(AddFreelancerVehicleCommand cmd) {
        log.info("Adding vehicle to FreelancerOrg fleet: orgId={} plate={}",
                cmd.ownerOrgId(), cmd.plateNumber());

        // 1. Enforce max fleet capacity (3 vehicles per org)
        Mono<Void> capacityCheck = vehicleRepository.countByOwnerOrgId(cmd.ownerOrgId())
                .flatMap(count -> {
                    if (count >= FreelancerVehicle.MAX_VEHICLES_PER_ORG) {
                        return Mono.error(new FreelancerFleetCapacityExceededException(cmd.ownerOrgId()));
                    }
                    return Mono.empty();
                });

        // 2. Check plate uniqueness within the org
        Mono<Void> plateCheck = vehicleRepository
                .existsByOwnerOrgIdAndPlateNumber(cmd.ownerOrgId(), cmd.plateNumber())
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.error(new IllegalArgumentException(
                                "Plate number already registered for this FreelancerOrg: "
                                        + cmd.plateNumber()));
                    }
                    return Mono.empty();
                });

        return capacityCheck
                .then(plateCheck)
                .then(Mono.defer(() -> {
                    FreelancerVehicle vehicle = FreelancerVehicle.register(
                            cmd.ownerOrgId(), cmd.type(), cmd.brand(), cmd.model(),
                            cmd.plateNumber(), cmd.maxCapacityKg(), cmd.volumeM3(),
                            cmd.fuelType(), cmd.fuelConsumptionLPer100km(),
                            cmd.registrationDocRef(), cmd.insuranceDocRef());
                    return vehicleRepository.save(vehicle);
                }))
                .flatMap(saved -> eventPublisher.publish(
                        FreelancerVehicleRegisteredEvent.of(saved.vehicleId(), saved.ownerOrgId(), saved.ownerActorId(),
                                saved.type(), saved.plateNumber(), saved.maxCapacityKg(),
                                saved.fuelType()))
                        .thenReturn(saved));
    }

    // ── AddPersonalVehicleUseCase (lot L3) ────────────────────────────────

    @Transactional
    @Override
    public Mono<FreelancerVehicle> addPersonalVehicle(AddPersonalVehicleCommand cmd) {
        log.info("Adding personal vehicle: actorId={} plate={}", cmd.ownerActorId(), cmd.plateNumber());
        Mono<Void> capacityCheck = vehicleRepository.countByOwnerActorId(cmd.ownerActorId())
                .flatMap(count -> count >= FreelancerVehicle.MAX_VEHICLES_PER_ORG
                        ? Mono.<Void>error(new IllegalStateException("An actor owns at most "
                                + FreelancerVehicle.MAX_VEHICLES_PER_ORG + " vehicles"))
                        : Mono.<Void>empty());
        return capacityCheck
                .then(plateFree(cmd.plateNumber(), cmd.vehicleId()))
                .then(Mono.fromCallable(() -> FreelancerVehicle.registerPersonal(cmd.vehicleId(), cmd.ownerActorId(),
                        cmd.type(), cmd.brand(), cmd.model(), cmd.plateNumber(), cmd.maxCapacityKg(), cmd.volumeM3(),
                        cmd.fuelType(), cmd.fuelConsumptionLPer100km(), cmd.registrationDocRef(),
                        cmd.insuranceDocRef())))
                .flatMap(vehicleRepository::save)
                .flatMap(saved -> eventPublisher.publish(
                        FreelancerVehicleRegisteredEvent.of(saved.vehicleId(), saved.ownerOrgId(), saved.ownerActorId(),
                                saved.type(), saved.plateNumber(), saved.maxCapacityKg(), saved.fuelType()))
                        .thenReturn(saved));
    }

    @Override
    public Flux<FreelancerVehicle> getPersonalVehicles(UUID ownerActorId) {
        return vehicleRepository.findByOwnerActorId(ownerActorId);
    }

    // ── UpdateFreelancerVehicleUseCase (lot L3) ───────────────────────────

    @Transactional
    @Override
    public Mono<FreelancerVehicle> updateVehicle(UpdateFreelancerVehicleCommand cmd) {
        log.info("Updating FreelancerVehicle {} plate={}", cmd.vehicleId(), cmd.plateNumber());
        return loadVehicle(cmd.vehicleId())
                .flatMap(v -> plateFree(cmd.plateNumber(), v.vehicleId())
                        .then(Mono.fromCallable(() -> v.update(cmd.type(), cmd.brand(), cmd.model(),
                                cmd.plateNumber(), cmd.maxCapacityKg(), cmd.volumeM3(), cmd.fuelType(),
                                cmd.fuelConsumptionLPer100km()))))
                .flatMap(vehicleRepository::save);
    }

    /** A plate is carried by one vehicle only (unique column): refused when another vehicle has it. */
    private Mono<Void> plateFree(String plateNumber, UUID vehicleId) {
        return vehicleRepository.existsByPlateNumberOnAnotherVehicle(plateNumber, vehicleId)
                .flatMap(taken -> taken
                        ? Mono.<Void>error(new IllegalStateException(
                                "Plate number already registered on another vehicle: " + plateNumber))
                        : Mono.<Void>empty());
    }

    // ── AddFreelancerEquipmentUseCase ─────────────────────────────────────

    @Override
    public Mono<FreelancerEquipment> addEquipment(AddFreelancerEquipmentCommand cmd) {
        log.info("Adding equipment to FreelancerOrg: orgId={} type={}", cmd.ownerOrgId(), cmd.type());
        FreelancerEquipment equipment = FreelancerEquipment.register(
                cmd.ownerOrgId(), cmd.type(), cmd.description(),
                cmd.maxCapacityKg(), cmd.ownedOrRented());
        return equipmentRepository.save(equipment);
    }

    // ── AssignFreelancerVehicleToMissionUseCase ───────────────────────────

    @Transactional
    @Override
    public Mono<FreelancerVehicle> assignToMission(AssignFreelancerVehicleToMissionCommand cmd) {
        log.info("Assigning FreelancerVehicle {} to mission {}", cmd.vehicleId(), cmd.deliveryId());
        return loadVehicle(cmd.vehicleId())
                .map(v -> v.assignToMission(cmd.deliveryId()))
                .flatMap(vehicleRepository::save)
                .flatMap(saved -> eventPublisher.publish(
                        FreelancerVehicleAssignedToMissionEvent.of(saved.vehicleId(),
                                saved.ownerOrgId(), saved.ownerActorId(), saved.type(), cmd.deliveryId()))
                        .thenReturn(saved));
    }

    // ── ReleaseFreelancerVehicleFromMissionUseCase ────────────────────────

    @Transactional
    @Override
    public Mono<FreelancerVehicle> releaseFromDelivery(UUID vehicleId, String deliveryId) {
        log.info("Releasing FreelancerVehicle {} from mission {}", vehicleId, deliveryId);
        return loadVehicle(vehicleId)
                .map(FreelancerVehicle::releaseFromMission)
                .flatMap(vehicleRepository::save)
                .flatMap(saved -> eventPublisher.publish(
                        FreelancerVehicleReleasedFromMissionEvent.of(saved.vehicleId(),
                                saved.ownerOrgId(), saved.ownerActorId(), deliveryId))
                        .thenReturn(saved));
    }

    // ── DeactivateFreelancerVehicleUseCase ────────────────────────────────

    @Override
    public Mono<FreelancerVehicle> deactivateVehicle(UUID vehicleId) {
        log.info("Deactivating FreelancerVehicle {}", vehicleId);
        return loadVehicle(vehicleId)
                .map(FreelancerVehicle::deactivate)
                .flatMap(vehicleRepository::save);
    }

    // ── GetFreelancerVehicleUseCase ───────────────────────────────────────

    @Override
    public Mono<FreelancerVehicle> getVehicle(UUID vehicleId) {
        return loadVehicle(vehicleId);
    }

    // ── ListFreelancerFleetUseCase ────────────────────────────────────────

    @Override
    public Flux<FreelancerVehicle> getAllVehicles(UUID ownerOrgId) {
        return vehicleRepository.findByOwnerOrgId(ownerOrgId);
    }

    @Override
    public Flux<FreelancerVehicle> getAvailableVehicles(UUID ownerOrgId) {
        return vehicleRepository.findAvailableByOwnerOrgId(ownerOrgId);
    }

    @Override
    public Flux<FreelancerEquipment> getActiveEquipments(UUID ownerOrgId) {
        return equipmentRepository.findActiveByOwnerOrgId(ownerOrgId);
    }

    @Override
    public Mono<Boolean> hasEquipmentOfType(UUID ownerOrgId, EquipmentType type) {
        return equipmentRepository.existsActiveByOwnerOrgIdAndType(ownerOrgId, type);
    }

    @Override
    public Mono<Boolean> hasCapacityFor(UUID vehicleId, double weightKg, double volumeM3) {
        return loadVehicle(vehicleId)
                .map(v -> v.canCarry(weightKg, volumeM3));
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private Mono<FreelancerVehicle> loadVehicle(UUID vehicleId) {
        return vehicleRepository.findById(vehicleId)
                .switchIfEmpty(Mono.error(new FreelancerVehicleNotFoundException(vehicleId)));
    }
}
