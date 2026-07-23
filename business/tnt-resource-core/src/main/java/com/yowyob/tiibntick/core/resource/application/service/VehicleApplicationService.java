package com.yowyob.tiibntick.core.resource.application.service;

import com.yowyob.tiibntick.core.resource.application.port.in.*;
import com.yowyob.tiibntick.core.resource.application.port.out.*;
import com.yowyob.tiibntick.core.resource.domain.event.*;
import com.yowyob.tiibntick.core.resource.domain.exception.VehicleNotFoundException;
import com.yowyob.tiibntick.core.resource.domain.model.*;
import com.yowyob.tiibntick.core.roles.adapter.in.web.RequirePermission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Application service orchestrating the Vehicle lifecycle within TiiBnTick.
 *
 * <p>Implements all vehicle-related inbound use cases: creation, assignment,
 * maintenance, retirement, location and odometer updates.</p>
 *
 * <p><b>Kernel integration (special case):</b> Vehicle is TNT-exclusive — there is no
 * Kernel counterpart and no {@code kernelVehicleId} field. The only Kernel integration
 * is {@code assignedDelivererId}, which is an {@code actorId} UUID referencing
 * RT-comops-actor-core. Before assigning a vehicle, this service optionally validates
 * the deliverer's existence in the Kernel via {@link KernelActorPort}.</p>
 *
 * <p>The Kernel validation is <em>best-effort and non-blocking</em>: if the Kernel is
 * unreachable, assignment proceeds with a WARN log (resilient design for informal
 * logistics contexts with intermittent connectivity).</p>
 *
 * <p>All operations are reactive (Project Reactor, non-blocking).</p>
 *
 * @author MANFOUO Braun
 */
@Service
public class VehicleApplicationService implements
        CreateVehicleUseCase,
        AssignVehicleUseCase,
        UnassignVehicleUseCase,
        SendVehicleToMaintenanceUseCase,
        CompleteVehicleMaintenanceUseCase,
        RetireVehicleUseCase,
        UpdateVehicleOdometerUseCase,
        UpdateVehicleLocationUseCase,
        GetVehicleUseCase,
        ListVehiclesByAgencyUseCase,
        ScheduleMaintenanceAlertUseCase {

    private static final Logger log = LoggerFactory.getLogger(VehicleApplicationService.class);

    private final VehicleRepository vehicleRepository;
    private final VehicleMaintenanceRecordRepository maintenanceRecordRepository;
    private final ResourceAllocationRepository allocationRepository;
    private final VehicleLocationPort locationPort;
    private final ResourceEventPublisherPort eventPublisher;

    /**
     * Outbound port to the Yowyob Kernel actor domain.
     * Used for best-effort deliverer (actor) validation before vehicle assignment.
     * Vehicle assignment is never hard-blocked by Kernel unavailability.
     */
    private final KernelActorPort kernelActorPort;

    public VehicleApplicationService(VehicleRepository vehicleRepository,
            VehicleMaintenanceRecordRepository maintenanceRecordRepository,
            ResourceAllocationRepository allocationRepository,
            VehicleLocationPort locationPort,
            ResourceEventPublisherPort eventPublisher,
            KernelActorPort kernelActorPort) {
        this.vehicleRepository = vehicleRepository;
        this.maintenanceRecordRepository = maintenanceRecordRepository;
        this.allocationRepository = allocationRepository;
        this.locationPort = locationPort;
        this.eventPublisher = eventPublisher;
        this.kernelActorPort = kernelActorPort;
    }

    // ── CreateVehicleUseCase ──────────────────────────────────────────────────

    @Transactional
    @Override
    @RequirePermission(resource = "resource", action = "write")
    public Mono<Vehicle> createVehicle(CreateVehicleCommand cmd) {
        return vehicleRepository.existsByRegistrationNumber(cmd.tenantId(), cmd.registrationNumber())
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.error(new IllegalArgumentException(
                                "Registration number already exists: " + cmd.registrationNumber()));
                    }
                    Vehicle vehicle = Vehicle.register(
                            cmd.tenantId(), cmd.organizationId(), cmd.agencyId(),
                            cmd.registrationNumber(), cmd.brand(), cmd.model(),
                            cmd.yearOfManufacture(), cmd.type(),
                            cmd.maxWeightKg(), cmd.maxVolumeM3(), cmd.hasRefrigeration());
                    return vehicleRepository.save(vehicle)
                            .flatMap(saved -> eventPublisher.publish(
                                    VehicleRegisteredEvent.of(saved.id(), saved.tenantId(),
                                            saved.agencyId(), saved.registrationNumber(),
                                            saved.type(), saved.capacity().maxWeightKg(),
                                            saved.capacity().maxVolumeM3()))
                                    .thenReturn(saved));
                });
    }

    // ── AssignVehicleUseCase ──────────────────────────────────────────────────

    /**
     * {@inheritDoc}
     *
     * <p>Before assigning, optionally validates that the deliverer (actorId) is active
     * in the Kernel. A WARN is logged if the actor cannot be verified — the assignment
     * still proceeds to support offline-first logistics contexts.</p>
     */
    @Transactional
    @Override
    @RequirePermission(resource = "resource", action = "reserve")
    public Mono<Vehicle> assignVehicle(AssignVehicleCommand cmd) {
        // Best-effort Kernel actor validation — non-blocking, never blocks assignment
        Mono<Void> actorCheck = kernelActorPort.isActiveActor(cmd.delivererId(), cmd.tenantId())
                .doOnNext(isActive -> {
                    if (!isActive) {
                        log.warn("Assigning vehicle to delivererId={} which is not found or inactive " +
                                "in Kernel actor registry — proceeding (offline-first fallback)",
                                cmd.delivererId());
                    } else {
                        log.debug("Kernel actor validation passed for delivererId={}", cmd.delivererId());
                    }
                })
                .then();

        return actorCheck.then(
                loadVehicle(cmd.tenantId(), cmd.vehicleId())
                        .map(v -> v.assign(cmd.delivererId()))
                        .flatMap(vehicleRepository::save)
                        .flatMap(saved -> {
                            ResourceAllocation allocation = ResourceAllocation.allocate(
                                    saved.tenantId(), saved.agencyId(), saved.id(),
                                    ResourceType.VEHICLE, cmd.delivererId(), cmd.missionId());
                            return allocationRepository.save(allocation)
                                    .then(eventPublisher.publish(VehicleAssignedEvent.of(
                                            saved.id(), saved.tenantId(), saved.agencyId(),
                                            cmd.delivererId(), cmd.missionId())))
                                    .thenReturn(saved);
                        }));
    }

    // ── UnassignVehicleUseCase ────────────────────────────────────────────────

    @Transactional
    @Override
    @RequirePermission(resource = "resource", action = "reserve")
    public Mono<Vehicle> unassignVehicle(UUID tenantId, UUID vehicleId) {
        return loadVehicle(tenantId, vehicleId)
                .flatMap(v -> {
                    UUID prevDeliverer = v.assignedDelivererId();
                    Vehicle unassigned = v.unassign();
                    return vehicleRepository.save(unassigned)
                            .flatMap(saved -> allocationRepository.findActiveByResource(tenantId, vehicleId)
                                    .flatMap(alloc -> allocationRepository.save(alloc.release()))
                                    .then(eventPublisher.publish(VehicleUnassignedEvent.of(
                                            saved.id(), saved.tenantId(), saved.agencyId(), prevDeliverer)))
                                    .thenReturn(saved));
                });
    }

    // ── SendVehicleToMaintenanceUseCase ───────────────────────────────────────

    @Transactional
    @Override
    @RequirePermission(resource = "resource", action = "write")
    public Mono<Vehicle> sendToMaintenance(SendVehicleToMaintenanceCommand cmd) {
        return loadVehicle(cmd.tenantId(), cmd.vehicleId())
                .map(v -> {
                    MaintenanceSchedule schedule = cmd.scheduledDate() != null
                            ? new MaintenanceSchedule(cmd.scheduledDate(), cmd.maintenanceType(),
                                    cmd.reason(), cmd.odometerThresholdKm())
                            : null;
                    return v.sendToMaintenance(schedule);
                })
                .flatMap(vehicleRepository::save)
                .flatMap(saved -> {
                    VehicleMaintenanceRecord rec = VehicleMaintenanceRecord.create(
                            saved.tenantId(), saved.agencyId(), saved.id(),
                            cmd.maintenanceType(), cmd.reason(), saved.odometerKm(),
                            cmd.scheduledDate(), cmd.technicianName());
                    return maintenanceRecordRepository.save(rec)
                            .then(eventPublisher.publish(VehicleSentToMaintenanceEvent.of(
                                    saved.id(), saved.tenantId(), saved.agencyId(),
                                    cmd.maintenanceType(), cmd.scheduledDate())))
                            .thenReturn(saved);
                });
    }

    // ── CompleteVehicleMaintenanceUseCase ─────────────────────────────────────

    @Override
    @RequirePermission(resource = "resource", action = "write")
    public Mono<Vehicle> completeMaintenance(UUID tenantId, UUID vehicleId, LocalDate completionDate) {
        return loadVehicle(tenantId, vehicleId)
                .map(Vehicle::completeMaintenance)
                .flatMap(vehicleRepository::save)
                .flatMap(saved -> maintenanceRecordRepository.findByVehicleId(tenantId, vehicleId)
                        .filter(r -> !r.isCompleted())
                        .next()
                        .flatMap(rec -> maintenanceRecordRepository.save(rec.complete(completionDate)))
                        .thenReturn(saved));
    }

    // ── RetireVehicleUseCase ──────────────────────────────────────────────────

    @Transactional
    @Override
    public Mono<Vehicle> retireVehicle(UUID tenantId, UUID vehicleId) {
        return loadVehicle(tenantId, vehicleId)
                .map(Vehicle::retire)
                .flatMap(vehicleRepository::save)
                .flatMap(saved -> locationPort.evictLocation(vehicleId)
                        .then(eventPublisher.publish(VehicleRetiredEvent.of(
                                saved.id(), saved.tenantId(), saved.agencyId())))
                        .thenReturn(saved));
    }

    // ── UpdateVehicleOdometerUseCase ──────────────────────────────────────────

    @Override
    public Mono<Vehicle> updateOdometer(UpdateVehicleOdometerCommand cmd) {
        return loadVehicle(cmd.tenantId(), cmd.vehicleId())
                .map(v -> v.updateOdometer(cmd.newOdometerKm()))
                .flatMap(vehicleRepository::save);
    }

    // ── UpdateVehicleLocationUseCase ──────────────────────────────────────────

    @Transactional
    @Override
    public Mono<Void> updateLocation(UpdateVehicleLocationCommand cmd) {
        return locationPort.updateLocation(cmd.vehicleId(), cmd.latitude(), cmd.longitude())
                .then(loadVehicle(cmd.tenantId(), cmd.vehicleId()))
                .map(v -> v.updateLocation(cmd.latitude(), cmd.longitude()))
                .flatMap(vehicleRepository::save)
                .then(eventPublisher.publish(VehicleLocationUpdatedEvent.of(
                        cmd.vehicleId(), cmd.tenantId(), null,
                        cmd.latitude(), cmd.longitude())));
    }

    // ── GetVehicleUseCase ─────────────────────────────────────────────────────

    @Override
    @RequirePermission(resource = "resource", action = "read")
    public Mono<Vehicle> getVehicle(UUID tenantId, UUID vehicleId) {
        return loadVehicle(tenantId, vehicleId);
    }

    // ── ListVehiclesByAgencyUseCase ───────────────────────────────────────────

    @Override
    @RequirePermission(resource = "resource", action = "read")
    public Flux<Vehicle> listByAgency(UUID tenantId, UUID agencyId, VehicleStatus statusFilter) {
        if (statusFilter != null) {
            return vehicleRepository.findByAgencyAndStatus(tenantId, agencyId, statusFilter);
        }
        return vehicleRepository.findByAgency(tenantId, agencyId);
    }

    // ── ScheduleMaintenanceAlertUseCase ───────────────────────────────────────

    @Override
    public Mono<Vehicle> scheduleMaintenanceAlert(ScheduleMaintenanceAlertCommand cmd) {
        return loadVehicle(cmd.tenantId(), cmd.vehicleId())
                .map(v -> {
                    MaintenanceSchedule schedule = new MaintenanceSchedule(
                            cmd.scheduledDate(), cmd.type(), cmd.reason(), cmd.odometerThresholdKm());
                    return v.scheduleNextMaintenance(schedule);
                })
                .flatMap(vehicleRepository::save);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private Mono<Vehicle> loadVehicle(UUID tenantId, UUID vehicleId) {
        return vehicleRepository.findById(tenantId, vehicleId)
                .switchIfEmpty(Mono.error(new VehicleNotFoundException(vehicleId)));
    }
}
