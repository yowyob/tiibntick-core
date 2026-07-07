package com.yowyob.tiibntick.core.incident.domain.enums;
/**
 * Confirmation state of a parcel handover between original and replacement driver.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */

public enum HandoverStatus {
    /** Neither driver has confirmed the handover yet. */

    PENDING, ORIGINAL_CONFIRMED, REPLACEMENT_CONFIRMED,
    /** Both drivers confirmed: handover is complete and will be blockchain-anchored. */

    BOTH_CONFIRMED, TIMED_OUT, CANCELLED
}
