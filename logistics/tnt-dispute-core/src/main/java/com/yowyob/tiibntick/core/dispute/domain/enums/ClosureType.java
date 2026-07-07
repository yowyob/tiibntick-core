package com.yowyob.tiibntick.core.dispute.domain.enums;

/**
 * Describes how a dispute was ultimately closed.
 *
 * @author MANFOUO Braun
 */
public enum ClosureType {
    RESOLVED_WITH_COMPENSATION,
    RESOLVED_WITHOUT_COMPENSATION,
    WITHDRAWN_BY_CLAIMANT,
    EXPIRED,
    ADMINISTRATIVE_CLOSURE
}
