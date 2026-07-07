package com.yowyob.tiibntick.bootstrap.health;

/**
 * Overall health status of TiiBnTick Core.
 * Maps to Spring Boot Actuator's {@code Status} semantics.
 *
 * @author MANFOUO Braun
 */
public enum HealthStatus {
    UP,
    DOWN,
    DEGRADED,
    UNKNOWN
}
