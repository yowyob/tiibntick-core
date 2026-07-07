package com.yowyob.tiibntick.core.resource.adapter.out.incident;

import com.yowyob.tiibntick.core.incident.port.outbound.IVehicleCompatibilityPort;
import com.yowyob.tiibntick.core.incident.domain.model.VehicleInfo;
import com.yowyob.tiibntick.core.resource.application.port.out.VehicleRepository;
import com.yowyob.tiibntick.core.resource.domain.model.VehicleStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * tnt-resource-core implementation of {@link IVehicleCompatibilityPort}
 * (outbound port from tnt-incident-core).
 *
 * <p>This adapter answers two questions that tnt-incident-core needs during incident
 * auto-resolution:
 * <ol>
 *   <li><strong>getVehicleInfo</strong> — what are the technical characteristics of a vehicle
 *       (capacity, refrigeration, type, owning agency)? Used to match vehicles to parcel
 *       requirements during substitution.</li>
 *   <li><strong>isVehicleAvailable</strong> — is a specific vehicle currently free for
 *       assignment? Used to validate a substitution candidate before committing.</li>
 * </ol>
 *
 * <p>The adapter also supports the inter-agency substitution lifecycle:
 * <ul>
 *   <li>{@link #placeVehicleInSubstitution} — transitions vehicle to
 *       {@code VehicleStatus.IN_INCIDENT_SUBSTITUTION}</li>
 *   <li>{@link #releaseVehicleFromSubstitution} — returns vehicle to {@code AVAILABLE}</li>
 * </ul>
 *
 * @author MANFOUO Braun
 * @see IVehicleCompatibilityPort
 */
@Slf4j
@Component
public class VehicleCompatibilityPortAdapter implements IVehicleCompatibilityPort {

    private final VehicleRepository vehicleRepository;

    public VehicleCompatibilityPortAdapter(VehicleRepository vehicleRepository) {
        this.vehicleRepository = vehicleRepository;
    }

    /**
     * Returns the technical characteristics of a vehicle for tnt-incident-core matching.
     *
     * <p>Populates:
     * <ul>
     *   <li>{@code vehicleId} — vehicle UUID</li>
     *   <li>{@code category} — {@code VehicleType.name()} (e.g., "VAN", "TRUCK")</li>
     *   <li>{@code maxCapacityKg} — maximum load in kilograms</li>
     *   <li>{@code hasRefrigeration} — cold chain capability flag</li>
     *   <li>{@code agencyId} — owning agency UUID</li>
     *   <li>{@code status} — current vehicle status name</li>
     * </ul>
     *
     * @param vehicleId the vehicle UUID
     * @return the vehicle info, or {@link VehicleInfo#notFound(UUID)} if not found
     */
    @Override
    public Mono<VehicleInfo> getVehicleInfo(UUID vehicleId) {
        return vehicleRepository.findByIdNoTenant(vehicleId)
                .map(vehicle -> VehicleInfo.builder()
                        .vehicleId(vehicle.id())
                        .category(vehicle.type().name())
                        .maxCapacityKg(vehicle.capacity().maxWeightKg())
                        .volumeM3(vehicle.capacity().maxVolumeM3())
                        .hasRefrigeration(vehicle.hasRefrigeration())
                        .agencyId(vehicle.agencyId())
                        .status(vehicle.status().name())
                        .build())
                .switchIfEmpty(Mono.fromCallable(() -> {
                    log.warn("getVehicleInfo: vehicle {} not found — returning notFound VehicleInfo",
                            vehicleId);
                    return VehicleInfo.notFound(vehicleId);
                }));
    }

    /**
     * Returns whether a vehicle is currently available for assignment.
     *
     * <p>A vehicle is available if its status is {@link VehicleStatus#AVAILABLE}.
     * This method is safe for cross-module calls — uses UUID lookup without tenant scoping.
     *
     * @param vehicleId the vehicle UUID
     * @return {@code true} if AVAILABLE; {@code false} if ASSIGNED, IN_MAINTENANCE, RETIRED, etc.
     */
    @Override
    public Mono<Boolean> isVehicleAvailable(UUID vehicleId) {
        return vehicleRepository.findByIdNoTenant(vehicleId)
                .map(v -> v.status() == VehicleStatus.AVAILABLE)
                .defaultIfEmpty(false);
    }

    /**
     * Places a vehicle in incident substitution status for inter-agency lending.
     *
     * <p>Called by tnt-incident-core when an {@code IncidentInterAgencyCooperation} is
     * approved and a vehicle from one agency needs to be lent to another for the duration
     * of the incident resolution.
     *
     * @param vehicleId         the vehicle to place in substitution
     * @param borrowingAgencyId the agency that will temporarily use this vehicle
     * @return empty Mono on success
     */
    @Override
    public Mono<Void> placeVehicleInSubstitution(UUID vehicleId, UUID borrowingAgencyId) {
        return vehicleRepository.findByIdNoTenant(vehicleId)
                .flatMap(vehicle -> {
                    try {
                        var updated = vehicle.placeInIncidentSubstitution(borrowingAgencyId);
                        return vehicleRepository.save(updated);
                    } catch (Exception e) {
                        log.warn("Cannot place vehicle {} in substitution (status={}): {}",
                                vehicleId, vehicle.status(), e.getMessage());
                        return Mono.just(vehicle);
                    }
                })
                .switchIfEmpty(Mono.fromRunnable(() ->
                        log.warn("placeVehicleInSubstitution: vehicle {} not found", vehicleId)))
                .then();
    }

    /**
     * Releases a vehicle from incident substitution, returning it to AVAILABLE.
     *
     * <p>Called when the incident is resolved and the inter-agency cooperation ends.
     *
     * @param vehicleId the vehicle to release
     * @return empty Mono on success
     */
    @Override
    public Mono<Void> releaseVehicleFromSubstitution(UUID vehicleId) {
        return vehicleRepository.findByIdNoTenant(vehicleId)
                .flatMap(vehicle -> {
                    if (vehicle.status() != VehicleStatus.IN_INCIDENT_SUBSTITUTION) {
                        log.debug("releaseVehicleFromSubstitution: vehicle {} is not IN_INCIDENT_SUBSTITUTION " +
                                "(status={}) — no-op", vehicleId, vehicle.status());
                        return Mono.just(vehicle);
                    }
                    return vehicleRepository.save(vehicle.releaseFromIncidentSubstitution());
                })
                .switchIfEmpty(Mono.fromRunnable(() ->
                        log.warn("releaseVehicleFromSubstitution: vehicle {} not found", vehicleId)))
                .then();
    }
}
