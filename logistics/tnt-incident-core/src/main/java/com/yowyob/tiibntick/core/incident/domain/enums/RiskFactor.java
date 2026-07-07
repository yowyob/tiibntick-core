package com.yowyob.tiibntick.core.incident.domain.enums;
/**
 * Individual risk dimension used by the risk scoring algorithm.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */

public enum RiskFactor {
    DRIVER_REPUTATION_SCORE, PARCEL_VALUE, ZONE_DANGER_INDEX,
    DELAY_SEVERITY, CARGO_SENSITIVITY, WEATHER_CONDITIONS,
    INCIDENT_HISTORY_DRIVER, MISSION_COMPLEXITY
}
