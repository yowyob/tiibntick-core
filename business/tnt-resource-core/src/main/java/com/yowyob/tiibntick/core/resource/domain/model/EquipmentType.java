package com.yowyob.tiibntick.core.resource.domain.model;

/**
 * Types of operational equipment used by TiiBnTick agents and FreelancerOrg fleet.
 *
 * <p>The original set covers standard operational equipment assigned to branch agents.
 * Values added in  cover the specialized physical equipment used by FreelancerOrg
 * vehicles for constrained package deliveries (fragile, perishable, pharmaceutical, etc.)
 *
 * @author MANFOUO Braun
 */
public enum EquipmentType {

    // ── Standard Agency/Agent equipment (original values, maintained) ──────

    /** Handheld QR code scanner for package tracking. */
    QR_SCANNER,

    /** Tablet device for digital delivery confirmation. */
    TABLET,

    /** Mobile payment terminal (MTN Mobile Money, Orange Money). */
    PAYMENT_TERMINAL,

    /** GPS tracking beacon attached to vehicle or parcel. */
    GPS_TRACKER,

    /** Package weighing scale. */
    WEIGHING_SCALE,

    /** Barcode reader for parcel identification. */
    BARCODE_SCANNER,

    // ── FreelancerOrg specialized physical equipment (added ) ──────────

    /**
     * Portable refrigerated box for cold-chain delivery.
     * Triggers DSL variable: {@code hasRefrigeratedBox == true}.
     * Required for {@code packageType == PERISHABLE} or {@code REFRIGERATED} deliveries.
     */
    REFRIGERATED_BOX,

    /**
     * Large cargo bag / saddlebag for motorcycle/bicycle delivery.
     * Suitable for STANDARD and DOCUMENTS packages.
     */
    CARGO_BAG,

    /**
     * Waterproof cover / rain protection for packages.
     * Relevant in tropical/rainy climate (Cameroon).
     */
    WATERPROOF_COVER,

    /**
     * IoT tracking beacon attached to a parcel during transit.
     * Enables real-time parcel tracking via tnt-realtime-core.
     */
    TRACKING_BEACON,

    /**
     * Security padlock for sealing delivery compartments.
     */
    PADLOCK,

    /**
     * Handheld parcel barcode/QR scanner (freelancer-grade, lighter than QR_SCANNER).
     */
    PARCEL_SCANNER,

    /**
     * Thermal insulation bag for pharmaceutical/food deliveries.
     * Less powerful than {@link #REFRIGERATED_BOX} — passive insulation only.
     */
    THERMAL_BAG,

    /**
     * Fragile foam padding / bubble wrap for fragile items.
     * Used when {@code packageType == FRAGILE}.
     */
    FRAGILE_FOAM,

    /**
     * Oversized cargo rack / extension rack mounted on the vehicle.
     * Required for {@code packageType == OVERSIZED}.
     */
    OVERSIZED_RACK
}
