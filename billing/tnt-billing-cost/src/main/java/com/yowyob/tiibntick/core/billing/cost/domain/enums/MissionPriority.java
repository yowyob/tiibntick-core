package com.yowyob.tiibntick.core.billing.cost.domain.enums;

/**
 * Mission priority level — affects time-value multiplier.
 * Higher priority implies the driver's time is valued more.
 *
 * @author MANFOUO Braun
 */
public enum MissionPriority {
    NORMAL(1.0),
    HIGH(1.30),
    URGENT(1.60),
    SAME_DAY(1.50),
    EXPRESS(2.0);

    private final double timeValueMultiplier;

    MissionPriority(double timeValueMultiplier) {
        this.timeValueMultiplier = timeValueMultiplier;
    }

    public double timeValueMultiplier() {
        return timeValueMultiplier;
    }
}
