package com.yowyob.tiibntick.core.resource.adapter.out.incident;

import com.yowyob.tiibntick.core.incident.port.outbound.IDriverAvailabilityPort;
import com.yowyob.tiibntick.core.incident.domain.model.DriverCandidate;
import com.yowyob.tiibntick.core.resource.application.port.out.ResourceAllocationRepository;
import com.yowyob.tiibntick.core.resource.application.port.out.VehicleRepository;
import com.yowyob.tiibntick.core.resource.domain.model.AllocationStatus;
import com.yowyob.tiibntick.core.resource.domain.model.Vehicle;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * tnt-resource-core implementation of {@link IDriverAvailabilityPort}
 * (outbound port from tnt-incident-core).
 *
 * <p>This adapter is called by tnt-incident-core's auto-resolution engine when an incident
 * requires a replacement driver. It finds eligible candidates by:
 * <ol>
 *   <li>Querying AVAILABLE vehicles in the tenant/agency with sufficient capacity,
 *       near the incident GPS location (haversine distance sort).</li>
 *   <li>For each vehicle, looking up the most recent {@code ResourceAllocation} to find
 *       the last deliverer who used that vehicle (candidate driver).</li>
 *   <li>Returning {@link DriverCandidate} objects for each eligible vehicle/driver pair.</li>
 * </ol>
 *
 * <p>If no recent allocation exists for an AVAILABLE vehicle, it is still returned as a
 * candidate with a {@code null} driverId — tnt-incident-core can handle this by querying
 * tnt-actor-core for any available deliverer.
 *
 * @author MANFOUO Braun
 * @see IDriverAvailabilityPort
 */
@Slf4j
@Component
public class DriverAvailabilityPortAdapter implements IDriverAvailabilityPort {

    private final VehicleRepository vehicleRepository;
    private final ResourceAllocationRepository allocationRepository;

    public DriverAvailabilityPortAdapter(VehicleRepository vehicleRepository,
                                          ResourceAllocationRepository allocationRepository) {
        this.vehicleRepository = vehicleRepository;
        this.allocationRepository = allocationRepository;
    }

    /**
     * Finds AVAILABLE vehicles near the incident location with sufficient capacity,
     * and maps each to a {@link DriverCandidate} by looking up the last known deliverer.
     *
     * @param tenantId          tenant scope
     * @param agencyId          optional agency filter (null = all agencies)
     * @param lat               incident GPS latitude
     * @param lng               incident GPS longitude
     * @param requiredCapacityKg minimum vehicle capacity in kg
     * @param vehicleCategory   optional vehicle type filter (e.g., "VAN", "TRUCK")
     * @return flux of driver candidates sorted by proximity to the incident
     */
    @Override
    public Flux<DriverCandidate> findEligibleReplacementDrivers(
            UUID tenantId, UUID agencyId, double lat, double lng,
            double requiredCapacityKg, String vehicleCategory) {

        log.debug("Finding replacement drivers: tenant={}, agency={}, location=({},{}), " +
                        "minCapacity={}kg, category={}",
                tenantId, agencyId, lat, lng, requiredCapacityKg, vehicleCategory);

        return findEligibleReplacementDrivers(tenantId, agencyId, lat, lng, requiredCapacityKg, vehicleCategory, null, false);
    }

    @Override
    public Flux<DriverCandidate> findEligibleReplacementDrivers(UUID tenantId, UUID agencyId,
                                                                 double lat, double lng, double requiredCapacityKg, String vehicleCategory,
                                                                 String freelancerOrgId, boolean searchWithinFreelancerOrg) {

        if (searchWithinFreelancerOrg && freelancerOrgId != null) {
            log.debug("Finding replacement drivers within FreelancerOrg {}: tenant={}, location=({},{}), minCapacity={}kg",
                    freelancerOrgId, tenantId, lat, lng, requiredCapacityKg);

            UUID orgId = UUID.fromString(freelancerOrgId);
            return vehicleRepository.findAvailableNear(tenantId, orgId, lat, lng, requiredCapacityKg, vehicleCategory)
                    .flatMap(vehicle -> resolveDriverCandidate(vehicle, lat, lng))
                    .switchIfEmpty(Flux.defer(() -> internalFindEligible(tenantId, agencyId, lat, lng, requiredCapacityKg, vehicleCategory)));
        }

        return internalFindEligible(tenantId, agencyId, lat, lng, requiredCapacityKg, vehicleCategory);
    }

    private Flux<DriverCandidate> internalFindEligible(UUID tenantId, UUID agencyId,
                                                        double lat, double lng, double requiredCapacityKg, String vehicleCategory) {
        return vehicleRepository.findAvailableNear(
                        tenantId, agencyId, lat, lng, requiredCapacityKg, vehicleCategory)
                .flatMap(vehicle -> resolveDriverCandidate(vehicle, lat, lng));
    }

    /**
     * Checks whether a specific driver (deliverer) is currently available.
     *
     * <p>A driver is considered available when they have no {@code ACTIVE}
     * resource allocation (i.e., no vehicle or equipment currently assigned to them
     * for an ongoing mission).
     *
     * @param driverId the deliverer UUID (actorId from tnt-actor-core)
     * @return {@code true} if the driver has no active allocation
     */
    @Override
    public Mono<Boolean> isDriverAvailable(UUID driverId) {
        // Check if any ACTIVE allocation exists for this deliverer
        // An ACTIVE allocation means the driver is currently on a mission
        return allocationRepository.findByUserAndStatus(
                        null /* no tenant scope for this cross-module check */,
                        driverId, AllocationStatus.ACTIVE)
                .hasElements()
                .map(hasActive -> !hasActive)
                .defaultIfEmpty(true); // No tenant scope? Default to available (safe fallback)
    }

    // ── Private helpers ────────────────────────────────────────────────

    /**
     * Builds a {@link DriverCandidate} for a vehicle by enriching it with:
     * <ul>
     *   <li>The last known deliverer (from most recent released allocation)</li>
     *   <li>The computed haversine distance from the incident location</li>
     * </ul>
     */
    private Mono<DriverCandidate> resolveDriverCandidate(Vehicle vehicle, double lat, double lng) {
        double distanceKm = haversineKm(lat, lng,
                vehicle.gpsLatitude() != null ? vehicle.gpsLatitude() : lat,
                vehicle.gpsLongitude() != null ? vehicle.gpsLongitude() : lng);

        return allocationRepository.findByResourceId(vehicle.tenantId(), vehicle.id())
                // Get the most recent allocation regardless of status (RELEASED = driver returned it)
                .filter(a -> a.assignedToUserId() != null)
                .next()
                .map(lastAlloc -> new DriverCandidate(
                        lastAlloc.assignedToUserId(),
                        vehicle.id(),
                        vehicle.agencyId(),
                        distanceKm,
                        0.0, // reputationScore not available in this context
                        vehicle.capacity().maxWeightKg(),
                        vehicle.type().name()
                ))
                .switchIfEmpty(
                        // No previous allocation — vehicle is new or never had a driver
                        Mono.just(new DriverCandidate(
                                null,       // tnt-incident-core must resolve the driver
                                vehicle.id(),
                                vehicle.agencyId(),
                                distanceKm,
                                0.0,
                                vehicle.capacity().maxWeightKg(),
                                vehicle.type().name()
                        )))
                .doOnNext(candidate -> log.debug(
                        "Driver candidate: vehicleId={}, driverId={}, distance={}km",
                        candidate.vehicleId(), candidate.driverId(), candidate.distanceKm()));
    }

    /**
     * Computes the approximate great-circle distance between two GPS points using
     * the Haversine formula. Accuracy is sufficient for sub-100km urban delivery contexts.
     *
     * @param lat1 first latitude in degrees
     * @param lon1 first longitude in degrees
     * @param lat2 second latitude in degrees
     * @param lon2 second longitude in degrees
     * @return distance in kilometres
     */
    private static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
}
