package com.yowyob.tiibntick.core.billing.pricing.domain.model.enums;

/**
 * Classification of the entity that owns a {@link com.yowyob.tiibntick.core.billing.pricing.domain.model.BillingPolicy}.
 *
 * <p>Determines the DSL access level, template catalog, and commission split
 * logic applicable to the policy.
 *
 * @author MANFOUO Braun
 */
public enum PolicyOwnerType {

    /** Traditional agency with branches and permanent staff. Full DSL access. */
    AGENCY,

    /**
     * Independent freelancer organization (sole proprietorship, 1-3 vehicles).
     * SIMPLIFIED DSL access. Uses FleetCostParameters for operational cost.
     */
    FREELANCER_ORG,

    /** Hub relay point operator. Needs hub storage pricing rules. */
    POINT,

    /** Link relay-point network operator. Needs network transit pricing rules. */
    LINK,

    /** TiiBnTick platform admin. Full DSL access. */
    ADMIN,

    /** TiiBnTick Market operator. Full DSL access. */
    MARKET
}
