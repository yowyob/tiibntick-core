package com.yowyob.tiibntick.core.incident.port.outbound;

import com.yowyob.tiibntick.core.incident.domain.model.DriverCandidate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.UUID;

/**
 * Outbound port: query available replacement drivers from tnt-resource-core.
 *
 * <p> — Extended to support FreelancerOrg fleet search when the incident
 * is on a FreelancerOrg-executed mission. When {@code searchWithinFreelancerOrg} is true,
 * the search prioritizes SUB_DELIVERER actors from the same FreelancerOrg before
 * falling back to the wider agency/platform pool.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 */
public interface IDriverAvailabilityPort {

    /**
     * Finds eligible replacement drivers for the given incident context.
     *
     * @param tenantId             tenant scope
     * @param agencyId             agency scope (null for freelancer-only search)
     * @param lat                  incident latitude
     * @param lng                  incident longitude
     * @param requiredCapacityKg   minimum vehicle capacity required
     * @param vehicleCategory      vehicle category filter (MOTO, VELO, VOITURE, etc.)
     * @return stream of eligible driver candidates, sorted by distance
     */
    Flux<DriverCandidate> findEligibleReplacementDrivers(UUID tenantId, UUID agencyId,
            double lat, double lng, double requiredCapacityKg, String vehicleCategory);

    /**
     * Finds eligible replacement drivers within a FreelancerOrg's fleet ().
     *
     * <p>Called when an incident occurs on a mission executed by a FreelancerOrg.
     * Searches among the org's SUB_DELIVERER actors first (within the same fleet),
     * then falls back to the broader pool if no internal candidates exist.
     *
     * @param tenantId                tenant scope
     * @param agencyId                agency scope
     * @param lat                     incident latitude
     * @param lng                     incident longitude
     * @param requiredCapacityKg      minimum vehicle capacity required
     * @param vehicleCategory         vehicle category filter
     * @param freelancerOrgId         UUID of the FreelancerOrg (for internal fleet search)
     * @param searchWithinFreelancerOrg when true, prioritizes the org's own fleet
     * @return stream of eligible driver candidates
     */
    Flux<DriverCandidate> findEligibleReplacementDrivers(UUID tenantId, UUID agencyId,
            double lat, double lng, double requiredCapacityKg, String vehicleCategory,
            String freelancerOrgId, boolean searchWithinFreelancerOrg);

    /**
     * Checks whether a specific driver is currently available for assignment.
     *
     * @param driverId the driver's actor UUID
     * @return true if the driver is available (no active mission)
     */
    Mono<Boolean> isDriverAvailable(UUID driverId);
}
