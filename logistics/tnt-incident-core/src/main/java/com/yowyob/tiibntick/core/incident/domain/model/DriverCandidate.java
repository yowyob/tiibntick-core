package com.yowyob.tiibntick.core.incident.domain.model;

import java.util.UUID;

/**
 * DTO representing a candidate replacement driver for an incident.
 *
 * <p> — Added {@code orgId} and {@code orgType} to track whether the
 * candidate belongs to a FreelancerOrg or an Agency.
 * 
 * @author MANFOUO Braun
 */
public record DriverCandidate(
        UUID driverId,
        UUID vehicleId,
        UUID agencyId,
        double distanceKm,
        double reputationScore,
        double capacityKg,
        String vehicleCategory,
        /** UUID of the org this driver belongs to (FreelancerOrg or Agency). */
        String orgId,
        /** "AGENCY" | "FREELANCER_ORG". Null for unaffiliated drivers. */
        String orgType
) {
    /** Backward-compatible constructor (Agency driver without org context). */
    public DriverCandidate(UUID driverId, UUID vehicleId, UUID agencyId,
            double distanceKm, double reputationScore,
            double capacityKg, String vehicleCategory) {
        this(driverId, vehicleId, agencyId, distanceKm, reputationScore, capacityKg,
                vehicleCategory, agencyId != null ? agencyId.toString() : null, "AGENCY");
    }
}
