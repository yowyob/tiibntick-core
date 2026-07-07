package com.yowyob.tiibntick.core.dispute.domain.enums;

/**
 * Business category of a dispute, used for routing and SLA configuration.
 *
 * @author MANFOUO Braun
 */
public enum DisputeCategory {
    /** Dispute arising from a Go platform mission (freelancer delivery). */
    MISSION_GO,
    /** Dispute arising from an Agency-managed permanent delivery. */
    MISSION_AGENCY,
    /** Dispute arising from an incident on a Link network node. */
    NETWORK_LINK,
    /** Dispute arising from a relay hub (Point) operation. */
    HUB_POINT,
    /** Client complaint submitted via the Market platform. */
    MARKET_CLAIM,
    /** Dispute filed by a freelancer regarding payment or mission terms. */
    FREELANCER_CLAIM
}
