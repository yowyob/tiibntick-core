package com.yowyob.tiibntick.core.dispute.domain.enums;

/**
 * Type of event recorded in a dispute's audit timeline.
 *
 * @author MANFOUO Braun
 */
public enum DisputeEventType {
    OPENED,
    MEDIATOR_ASSIGNED,
    EVIDENCE_SUBMITTED,
    EVIDENCE_REQUESTED,
    INVESTIGATION_STARTED,
    MEDIATION_STARTED,
    RULING_ISSUED,
    ESCALATED,
    COMPENSATION_APPROVED,
    COMPENSATION_PAID,
    CLOSED,
    WITHDRAWN,
    SLA_BREACHED,
    STATUS_CHANGED,
    COMMENT_ADDED
}
