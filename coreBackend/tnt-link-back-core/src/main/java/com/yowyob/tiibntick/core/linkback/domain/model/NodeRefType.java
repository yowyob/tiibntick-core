package com.yowyob.tiibntick.core.linkback.domain.model;

/**
 * What kind of existing tnt-actor-core/tnt-organization-core entity a
 * {@link NetworkNode} extends. {@code refId} then points at that entity's
 * own id — never duplicated here.
 */
public enum NodeRefType {
    /** tnt-actor-core DelivererProfile (agency-attached deliverer). */
    DELIVERER,
    /** tnt-actor-core FreelancerProfile (independent deliverer). */
    FREELANCER,
    /** tnt-actor-core ClientProfile (sender). */
    CLIENT,
    /** tnt-actor-core RelayOperatorProfile + tnt-organization-core HubRelais. */
    RELAY_OPERATOR,
    /** tnt-organization-core Agency. */
    AGENCY
}
