package com.yowyob.tiibntick.core.incident.domain.enums;
/**
 * Lifecycle state of an inter-agency cooperation request.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */

public enum CooperationStatus {
    /** Cooperation request has been sent to the responding agency. */

    REQUESTED, ACCEPTED, IN_PROGRESS, COMPLETED, REJECTED, CANCELLED
}
