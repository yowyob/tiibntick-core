package com.yowyob.tiibntick.core.agency.fleet.application.service;

import com.yowyob.tiibntick.common.exception.TntConflictException;
import com.yowyob.tiibntick.common.exception.TntNotFoundException;
import com.yowyob.tiibntick.common.exception.TntValidationException;
import com.yowyob.tiibntick.core.agency.fleet.adapter.in.web.dto.VehicleResponse;
import com.yowyob.tiibntick.core.agency.fleet.adapter.out.persistence.AgencyVehicleR2dbcRepository;
import com.yowyob.tiibntick.core.agency.fleet.application.mapper.FleetMapper;
import com.yowyob.tiibntick.core.agency.fleet.domain.Vehicle;
import com.yowyob.tiibntick.core.agency.fleet.domain.vo.VehicleSource;
import com.yowyob.tiibntick.core.agency.fleet.domain.vo.VehicleType;
import com.yowyob.tiibntick.core.agency.fleet.adapter.out.clients.ResourceCorePort;
import com.yowyob.tiibntick.core.agency.org.application.service.AgencyBranchService;
import com.yowyob.tiibntick.core.agency.org.application.service.AgencyRegistryService;
import com.yowyob.tiibntick.core.agency.workforce.adapter.out.persistence.DelivererR2dbcRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * Agency fleet registry — syncs new vehicles with tnt-resource-core when not pre-linked.
 */
@Service
@RequiredArgsConstructor
public class FleetService {

    private final AgencyVehicleR2dbcRepository vehicleRepo;
    private final AgencyRegistryService agencyRegistry;
    private final AgencyBranchService branchService;
    private final DelivererR2dbcRepository delivererRepo;
    private final ResourceCorePort resourceCore;

    @Transactional
    public Mono<VehicleResponse> add(AddInput input) {
        return vehicleRepo.existsByLicensePlateAndTenantId(input.licensePlate(), input.tenantId())
                .flatMap(exists -> {
                    if (Boolean.TRUE.equals(exists)) {
                        return Mono.error(new TntConflictException("License plate already registered"));
                    }
                    return validateAgencyAndBranch(input.tenantId(), input.agencyId(), input.branchId())
                            .then(agencyRegistry.getById(input.tenantId(), input.agencyId()))
                            .flatMap(agency -> {
                                Mono<UUID> coreVehicleIdMono = input.coreVehicleId() != null
                                        ? Mono.just(input.coreVehicleId())
                                        : resourceCore.registerVehicle(new ResourceCorePort.RegisterVehicleRequest(
                                                input.tenantId(),
                                                agency.getKernelOrganizationId(),
                                                agency.getCoreAgencyId(),
                                                input.licensePlate(),
                                                input.brand(),
                                                input.model(),
                                                input.year(),
                                                input.vehicleType()));
                                return coreVehicleIdMono.flatMap(coreVehicleId -> {
                                    Instant now = Instant.now();
                                    VehicleSource source = input.source() != null
                                            ? input.source() : VehicleSource.AGENCY;
                                    Vehicle vehicle = Vehicle.add(
                                            UUID.randomUUID(), input.tenantId(), input.agencyId(),
                                            input.branchId(), input.licensePlate(), input.brand(),
                                            input.model(), input.year(), input.vehicleType(),
                                            source, input.fleetmanVehicleId(), now);
                                    vehicle.linkCoreVehicle(coreVehicleId, now);
                                    return vehicleRepo.save(FleetMapper.toEntity(vehicle));
                                });
                            })
                            .map(FleetMapper::toDomain)
                            .map(VehicleResponse::from);
                });
    }

    @Transactional
    public Mono<VehicleResponse> linkFleetMan(UUID tenantId, UUID vehicleId,
                                              String fleetmanVehicleId, VehicleSource source) {
        return requireVehicle(vehicleId, tenantId)
                .flatMap(vehicle -> {
                    vehicle.linkFleetMan(fleetmanVehicleId,
                            source != null ? source : VehicleSource.AGENCY, Instant.now());
                    return vehicleRepo.save(FleetMapper.toEntity(vehicle));
                })
                .map(FleetMapper::toDomain)
                .map(VehicleResponse::from);
    }

    @Transactional
    public Mono<VehicleResponse> linkCoreVehicle(UUID tenantId, UUID vehicleId, UUID coreVehicleId) {
        return requireVehicle(vehicleId, tenantId)
                .flatMap(vehicle -> {
                    vehicle.linkCoreVehicle(coreVehicleId, Instant.now());
                    return vehicleRepo.save(FleetMapper.toEntity(vehicle));
                })
                .map(FleetMapper::toDomain)
                .map(VehicleResponse::from);
    }

    @Transactional
    public Mono<VehicleResponse> assign(UUID tenantId, UUID vehicleId, UUID delivererId) {
        return requireVehicle(vehicleId, tenantId)
                .flatMap(vehicle -> delivererRepo.findByIdAndTenantId(delivererId, tenantId)
                        .switchIfEmpty(Mono.error(new TntNotFoundException(
                                "DELIVERER_NOT_FOUND", "Deliverer not found: " + delivererId)))
                        .flatMap(deliverer -> {
                            if (!vehicle.getAgencyId().equals(deliverer.getAgencyId())) {
                                return Mono.error(new TntValidationException(
                                        "Deliverer does not belong to the vehicle agency"));
                            }
                            vehicle.assign(delivererId, Instant.now());
                            return vehicleRepo.save(FleetMapper.toEntity(vehicle));
                        }))
                .map(FleetMapper::toDomain)
                .map(VehicleResponse::from);
    }

    @Transactional
    public Mono<VehicleResponse> unassign(UUID tenantId, UUID vehicleId) {
        return mutate(vehicleId, tenantId, v -> v.unassign(Instant.now()));
    }

    @Transactional
    public Mono<VehicleResponse> sendToMaintenance(UUID tenantId, UUID vehicleId) {
        return mutate(vehicleId, tenantId, v -> v.sendToMaintenance(Instant.now()));
    }

    @Transactional
    public Mono<VehicleResponse> returnFromMaintenance(UUID tenantId, UUID vehicleId) {
        return mutate(vehicleId, tenantId, v -> v.returnFromMaintenance(Instant.now()));
    }

    @Transactional
    public Mono<VehicleResponse> retire(UUID tenantId, UUID vehicleId) {
        return mutate(vehicleId, tenantId, v -> v.retire(Instant.now()));
    }

    public Mono<VehicleResponse> getById(UUID tenantId, UUID vehicleId) {
        return requireVehicle(vehicleId, tenantId).map(VehicleResponse::from);
    }

    public Flux<VehicleResponse> listByAgency(UUID tenantId, UUID agencyId) {
        return agencyRegistry.getById(tenantId, agencyId)
                .thenMany(vehicleRepo.findByAgencyIdAndTenantId(agencyId, tenantId))
                .map(FleetMapper::toDomain)
                .map(VehicleResponse::from);
    }

    private Mono<VehicleResponse> mutate(UUID vehicleId, UUID tenantId,
                                         java.util.function.Consumer<Vehicle> action) {
        return requireVehicle(vehicleId, tenantId)
                .flatMap(vehicle -> {
                    action.accept(vehicle);
                    return vehicleRepo.save(FleetMapper.toEntity(vehicle));
                })
                .map(FleetMapper::toDomain)
                .map(VehicleResponse::from);
    }

    private Mono<Vehicle> requireVehicle(UUID vehicleId, UUID tenantId) {
        return vehicleRepo.findByIdAndTenantId(vehicleId, tenantId)
                .map(FleetMapper::toDomain)
                .switchIfEmpty(Mono.error(new TntNotFoundException(
                        "VEHICLE_NOT_FOUND", "Vehicle not found: " + vehicleId)));
    }

    private Mono<Void> validateAgencyAndBranch(UUID tenantId, UUID agencyId, UUID branchId) {
        Mono<Void> agencyCheck = agencyRegistry.getById(tenantId, agencyId).then();
        if (branchId == null) {
            return agencyCheck;
        }
        return agencyCheck.then(branchService.getById(tenantId, branchId)
                .flatMap(branch -> {
                    if (!branch.agencyId().equals(agencyId)) {
                        return Mono.error(new TntValidationException(
                                "Branch does not belong to agency: " + agencyId));
                    }
                    return Mono.empty();
                }));
    }

    public record AddInput(
            UUID tenantId, UUID agencyId, UUID branchId,
            String licensePlate, String brand, String model,
            int year, VehicleType vehicleType, UUID coreVehicleId,
            VehicleSource source, String fleetmanVehicleId) {

        public AddInput(UUID tenantId, UUID agencyId, UUID branchId,
                        String licensePlate, String brand, String model,
                        int year, VehicleType vehicleType, UUID coreVehicleId) {
            this(tenantId, agencyId, branchId, licensePlate, brand, model,
                    year, vehicleType, coreVehicleId, VehicleSource.AGENCY, null);
        }
    }
}
