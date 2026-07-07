package com.yowyob.tiibntick.core.billing.dsl.domain.model;

/**
 * Access level controlling which DSL variables and operators a billing policy
 * owner may use when defining their pricing rules.
 *
 * <p>Access levels are assigned per actor type in {@code tnt-billing-pricing}:
 * <ul>
 *   <li>Agencies and Admin → {@link #FULL}</li>
 *   <li>FreelancerOrg owners and PointOperators → {@link #SIMPLIFIED}</li>
 *   <li>Clients / Destinataires → {@link #NONE}</li>
 * </ul>
 *
 * <p>The {@link com.yowyob.tiibntick.core.billing.dsl.application.service.DslAccessValidator}
 * enforces these restrictions during rule creation and update.</p>
 *
 * @author MANFOUO Braun
 */
public enum DslAccessLevel {

    /**
     * Full DSL access — all variables, operators, and nesting depth allowed.
     * Intended for Agencies and TiiBnTick Admin.
     */
    FULL,

    /**
     * Simplified DSL access — restricted variable set, max 3-level nesting,
     * max 20 rules per policy.
     * Intended for FreelancerOrg OWNER and Hub Point operators.
     */
    SIMPLIFIED,

    /**
     * No DSL authoring access — the actor cannot define or modify pricing rules.
     * Throws {@link com.yowyob.tiibntick.core.billing.dsl.domain.exception.UnsupportedDslAccessException}
     * if DSL authoring is attempted.
     */
    NONE
}
