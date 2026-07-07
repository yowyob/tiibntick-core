package com.yowyob.tiibntick.core.organization.domain.enums;

/**
 * Declared operational specialization of a FreelancerOrganization.
 *
 * <p>Specializations are used to filter relevant mission types during matching
 * and to display capability badges to clients in TiiBnTick Go and Market.
 *
 * @author MANFOUO Braun
 */
public enum FreelancerSpecialization {

    /** Deliveries in zones without formal street addresses (informal POI-based). */
    LAST_MILE_INFORMAL_ZONE,

    /** Medical supplies, pharmaceuticals, hospital/clinic deliveries. */
    MEDICAL_DELIVERY,

    /** Long-distance deliveries between cities (e.g., Yaoundé–Douala). */
    INTER_CITY,

    /** Heavy or large-volume cargo (requires van or camionnette). */
    BULK_CARGO,

    /** Guaranteed 24-hour or same-day express delivery. */
    EXPRESS_24H,

    /** Rural or peri-urban zones with difficult road access. */
    RURAL_ZONE,

    /** Night-time deliveries (typically 22:00 – 06:00). */
    NIGHT_DELIVERY,

    /** Refrigerated delivery requiring cold-chain equipment. */
    REFRIGERATED,

    /** Luxury or high-value item delivery with enhanced security. */
    LUXURY_ITEMS
}
