package com.yowyob.tiibntick.core.billing.pricing.domain.model.enums;

/**
 * Controls how multiple matching {@link com.yowyob.tiibntick.core.billing.pricing.domain.model.SpecialSurchargeRule}
 * instances are stacked when more than one rule matches a given {@link com.yowyob.tiibntick.core.billing.dsl.domain.model.PricingContext}.
 *
 * @author MANFOUO Braun
 */
public enum SurchargeStackMode {

    /**
     * All matching surcharges are summed together.
     * Example: +500 XAF (fragile) + 15% (night) both apply.
     */
    CUMULATIVE,

    /**
     * Only the surcharge with the highest computed amount is applied.
     * All other matching surcharges are ignored.
     * Example: night (15%) and weekend (10%) → only night applies.
     */
    EXCLUSIVE_HIGHEST,

    /**
     * All matching surcharges are summed, but the total is capped at
     * {@link com.yowyob.tiibntick.core.billing.pricing.domain.model.SpecialSurchargeRule#getCapAmount()}.
     * Ensures surcharges never exceed a defined ceiling.
     */
    CAPPED
}
