package com.yowyob.tiibntick.core.billing.templates.domain.model;

/**
 * Categorizes billing policy templates by their primary purpose.
 *
 * <p>Each template belongs to exactly one category, which is used for
 * catalog filtering and UI grouping in the TiiBnTick platform.
 *
 * @author MANFOUO Braun
 * @version 1.0
 * @since 2026-05-26
 */
public enum TemplateCategory {

    /**
     * Base pricing templates — simple weight + distance pricing.
     * Suitable for any actor starting without complexity.
     */
    BASE,

    /**
     * Specialty package templates — fragile, perishable, pharmaceutical, etc.
     * Includes specific surcharges for constrained packages.
     */
    SPECIALTY,

    /**
     * Loyalty and customer retention templates.
     * Progressive discounts based on client transaction history.
     */
    LOYALTY,

    /**
     * Time-based pricing templates — peak hours, night rates, weekends, holidays.
     */
    TIME,

    /**
     * Weather-adaptive templates — dynamic surcharges based on weather conditions.
     * Relevant in tropical/rainy climate contexts (Cameroon).
     */
    WEATHER,

    /**
     * Hub (Point Relais) storage fee templates — duration-based storage pricing.
     * Only applicable to POINT owner type.
     */
    HUB,

    /**
     * Network transit templates — inter-node routing fees.
     * Only applicable to LINK owner type.
     */
    NETWORK,

    /**
     * Commission distribution templates — deliverer remuneration models.
     * Applicable to Agency (permanent deliverers) and FreelancerOrg (sub-deliverers).
     */
    COMMISSION,

    /**
     * Marketplace commission templates — platform fee models.
     * Applicable to MARKET and ADMIN owner types.
     */
    MARKETPLACE
}
