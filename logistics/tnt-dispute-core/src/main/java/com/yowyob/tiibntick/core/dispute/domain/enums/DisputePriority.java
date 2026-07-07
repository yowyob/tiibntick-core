package com.yowyob.tiibntick.core.dispute.domain.enums;

/**
 * Priority level of a dispute, determining SLA timelines.
 *
 * <ul>
 *   <li>CRITICAL — response within 24 h</li>
 *   <li>HIGH      — response within 48 h</li>
 *   <li>NORMAL    — response within 72 h</li>
 *   <li>LOW        — response within 7 days</li>
 * </ul>
 *
 * @author MANFOUO Braun
 */
public enum DisputePriority {
    CRITICAL,
    HIGH,
    NORMAL,
    LOW
}
