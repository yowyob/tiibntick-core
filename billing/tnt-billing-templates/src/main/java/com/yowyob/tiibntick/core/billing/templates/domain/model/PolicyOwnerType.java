package com.yowyob.tiibntick.core.billing.templates.domain.model;

/**
 * Identifies the type of actor that owns a BillingPolicy or can use a template.
 *
 * <p>This enum is replicated here to avoid a tight compile-time dependency on
 * {@code tnt-billing-pricing} at the domain layer. The outbound port adapters
 * are responsible for translating between these types and the pricing module's
 * own enum.
 *
 * <p>Template applicability is filtered by this type so that, for example,
 * {@code TPL-HUB_STORAGE} is only shown to {@code POINT} operators.
 *
 * @author MANFOUO Braun
 * @version 1.0
 * @since 2026-05-26
 */
public enum PolicyOwnerType {

    /**
     * Certified delivery agency with branches and permanent deliverers.
     * Has access to FULL DSL and all template categories.
     */
    AGENCY,

    /**
     * Freelancer organization (sole proprietorship micro-fleet).
     * Has access to SIMPLIFIED DSL and most template categories.
     */
    FREELANCER_ORG,

    /**
     * Point Relais (hub relay operator).
     * Can define hub storage fees; only HUB template category is exclusive to this type.
     */
    POINT,

    /**
     * Link network operator (inter-node relay network).
     * Can define network transit fees; only NETWORK template category is exclusive.
     */
    LINK,

    /**
     * TiiBnTick platform administrator.
     * Has unrestricted access to all templates and DSL.
     */
    ADMIN,

    /**
     * TiiBnTick Market operator.
     * Can define marketplace commission policies.
     */
    MARKET
}
