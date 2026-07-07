package com.yowyob.tiibntick.core.organization.domain.enums;

/**
 * Difficulty level of access to a geographic service zone.
 *
 * <p>Used by the billing engine to compute surcharges for difficult zones
 * and by the routing engine to adjust ETA estimates.
 *
 * @author MANFOUO Braun
 */
public enum ZoneAccessDifficulty {

    /** Normal urban roads — no access difficulty. */
    LOW,

    /** Mixed roads — some traffic or minor unpaved sections. */
    MEDIUM,

    /** Significant access difficulty — unpaved roads, steep terrain, seasonal flooding. */
    HIGH,

    /** Extreme difficulty — only accessible by specific vehicle types, enclave zones. */
    VERY_HIGH
}
