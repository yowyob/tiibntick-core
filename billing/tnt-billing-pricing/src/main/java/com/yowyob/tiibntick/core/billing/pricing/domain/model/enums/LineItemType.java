package com.yowyob.tiibntick.core.billing.pricing.domain.model.enums;

/**
 * Classifies each line item in a price breakdown.
 *
 * <h3> additions</h3>
 * <ul>
 *   <li>{@link #SPECIAL_SURCHARGE} — conditional surcharge from a SpecialSurchargeRule (e.g. refrigeration)</li>
 *   <li>{@link #HUB_STORAGE} — storage fee at a relay hub point</li>
 *   <li>{@link #NETWORK_TRANSIT} — per-hop transit fee in a Link network</li>
 *   <li>{@link #FLEET_COST} — operational cost component (FreelancerOrg fleet)</li>
 * </ul>
 *
 * @author MANFOUO Braun
 */
public enum LineItemType {
    BASE,
    PER_KM,
    PER_KG,
    SURCHARGE,
    PROMOTION,
    LOYALTY,
    PLATFORM_FEE,
    COMMISSION,
    /**  — Special conditional surcharge (SpecialSurchargeRule). */
    SPECIAL_SURCHARGE,
    /**  — Hub relay storage fee. */
    HUB_STORAGE,
    /**  — Link network per-hop transit fee. */
    NETWORK_TRANSIT,
    /**  — FreelancerOrg fleet operational cost component. */
    FLEET_COST
}
