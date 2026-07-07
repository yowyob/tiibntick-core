package com.yowyob.tiibntick.core.incident.domain.enums;

/**
 * Ordered severity scale for incidents, from NEGLIGIBLE to FATAL.
 * Drives auto-escalation thresholds and resolution mode selection.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */
public enum IncidentSeverity {

    /** Minor inconvenience with no impact on delivery SLA. */
    NEGLIGIBLE(0),

    /** Slight delay expected; automatic resolution likely succeeds. */
    LOW(1),

    /** Noticeable impact on delivery; automatic resolution attempted first. */
    MEDIUM(2),

    /** Significant disruption; agency intervention may be required. */
    HIGH(3),

    /** Severe disruption requiring immediate escalation to an agency manager. */
    CRITICAL(4),

    /** Life-threatening situation or total mission failure requiring emergency response. */
    FATAL(5);

    private final int level;

    IncidentSeverity(int level) {
        this.level = level;
    }

    /**
     * Returns the numeric severity level (0 = least, 5 = most severe).
     *
     * @return the severity level
     */
    public int getLevel() {
        return level;
    }

    /**
     * Returns {@code true} if this severity is at least as severe as the given threshold.
     *
     * @param other the threshold severity
     * @return {@code true} if {@code this.level >= other.level}
     */
    public boolean isAtLeast(IncidentSeverity other) {
        return this.level >= other.level;
    }

    /**
     * Returns {@code true} if this severity mandates immediate escalation without attempting
     * automatic resolution first.
     *
     * @return {@code true} for CRITICAL and FATAL
     */
    public boolean requiresImmediateEscalation() {
        return this == CRITICAL || this == FATAL;
    }
}
