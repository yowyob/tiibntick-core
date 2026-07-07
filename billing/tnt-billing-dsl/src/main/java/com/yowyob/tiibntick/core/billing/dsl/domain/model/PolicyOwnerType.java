package com.yowyob.tiibntick.core.billing.dsl.domain.model;

/**
 * Classification of the owner of a billing policy.
 *
 * <p>Used in the {@link PricingContext} to determine which policy template
 * catalog and which DSL access level apply to a given actor.
 * Also used in rule condition expressions via the {@code policyOwnerType} DSL variable.
 *
 * @author MANFOUO Braun
 */
public enum PolicyOwnerType {

    /** Traditional agency with branches and permanent staff. */
    AGENCY,

    /**
     * Independent freelancer organization (sole proprietorship).
     * Uses DSL access level SIMPLIFIED.
     */
    FREELANCER_ORG,

    /** Hub relay point operator. */
    POINT,

    /** Link relay-point network operator. */
    LINK,

    /** TiiBnTick platform administrator. */
    ADMIN,

    /** Market-place operator. */
    MARKET
}
