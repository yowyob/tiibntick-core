package com.yowyob.tiibntick.core.incident.domain.enums;

/**
 * What a resolved incident does to its delivery (spec §1.3, mandatory on every move to {@code RESOLVED}).
 */
public enum IncidentDeliveryOutcome {
    /** The delivery goes on (rows D14/D16/D18). */
    RESUMED,
    /** "Incident without outcome": the delivery fails (row D20, [C.1]); the parcel returns if collected (QO-5). */
    FAILED,
    /** Transferred to a dispute (QO-15): the dispute decides the delivery and the money; no delivery effect here. */
    DISPUTE
}
