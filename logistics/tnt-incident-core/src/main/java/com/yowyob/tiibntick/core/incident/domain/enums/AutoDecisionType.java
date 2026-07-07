package com.yowyob.tiibntick.core.incident.domain.enums;
/**
 * Automated decision that the system can take without human intervention.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */

public enum AutoDecisionType {
    AUTO_REASSIGN_DRIVER, AUTO_SUBSTITUTE_VEHICLE, AUTO_REROUTE_MISSION,
    AUTO_ESCALATE_AGENCY, AUTO_REQUEST_INTERAGENCY, AUTO_NOTIFY_ALL_PARTIES,
    AUTO_FREEZE_PAYMENT, AUTO_TRANSFER_CARGO_HUB, AUTO_CLOSE_INCIDENT,
    AUTO_FLAG_FRAUD, AUTO_TRIGGER_DISPUTE
}
