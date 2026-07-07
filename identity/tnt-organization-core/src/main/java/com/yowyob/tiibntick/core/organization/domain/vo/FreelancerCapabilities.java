package com.yowyob.tiibntick.core.organization.domain.vo;

import com.yowyob.tiibntick.core.organization.domain.enums.FreelancerSpecialization;

/**
 * Value Object describing the operational capabilities declared by a FreelancerOrganization.
 *
 * <p>Capabilities are used during mission matching (to filter eligible freelancers),
 * during billing (to trigger equipment-based surcharges), and in the UI (to display
 * specialization badges to clients).
 *
 * <p>The {@code acceptedPackageTypeCodes} and {@code specializationCodes} are stored
 * as comma-separated strings in the database (simple, queryable via LIKE when needed).
 *
 * @param maxWeightKg             Maximum parcel weight the freelancer can carry (kg)
 * @param maxDistanceKm           Maximum delivery distance in km
 * @param worksWeekends           Whether weekend missions are accepted
 * @param worksNights             Whether night deliveries (22h–06h) are accepted
 * @param acceptedPackageTypeCodes Comma-separated PackageType codes (e.g., "STANDARD,FRAGILE")
 * @param specializationCodes      Comma-separated FreelancerSpecialization codes
 *
 * @author MANFOUO Braun
 */
public record FreelancerCapabilities(
        double maxWeightKg,
        double maxDistanceKm,
        boolean worksWeekends,
        boolean worksNights,
        String acceptedPackageTypeCodes,
        String specializationCodes
) {

    /**
     * Default capabilities assigned at registration before the freelancer
     * completes their capability profile.
     *
     * @return minimal capabilities (5 kg, 10 km, no weekends/nights, standard parcels only)
     */
    public static FreelancerCapabilities defaults() {
        return new FreelancerCapabilities(
                5.0, 10.0, false, false, "STANDARD", ""
        );
    }

    /**
     * Checks whether the freelancer has declared a given specialization.
     *
     * @param specialization the specialization to check
     * @return {@code true} if the specialization code is present in {@code specializationCodes}
     */
    public boolean hasSpecialization(FreelancerSpecialization specialization) {
        if (specializationCodes == null || specializationCodes.isBlank()) {
            return false;
        }
        for (String code : specializationCodes.split(",")) {
            if (specialization.name().equals(code.trim())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks whether the freelancer accepts parcels of the given type code.
     *
     * @param packageTypeCode the PackageType enum name to check (e.g., "FRAGILE")
     * @return {@code true} if the type is listed in {@code acceptedPackageTypeCodes}
     */
    public boolean acceptsPackageType(String packageTypeCode) {
        if (acceptedPackageTypeCodes == null || acceptedPackageTypeCodes.isBlank()) {
            return false;
        }
        for (String code : acceptedPackageTypeCodes.split(",")) {
            if (packageTypeCode.equals(code.trim())) {
                return true;
            }
        }
        return false;
    }
}
