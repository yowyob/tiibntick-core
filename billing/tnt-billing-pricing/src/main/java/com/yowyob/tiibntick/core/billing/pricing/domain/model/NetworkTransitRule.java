package com.yowyob.tiibntick.core.billing.pricing.domain.model;

import com.yowyob.tiibntick.core.billing.dsl.domain.model.Money;
import lombok.Builder;
import lombok.extern.jackson.Jacksonized;
import lombok.Value;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Pricing rule for multi-hop parcel transit within a Link relay network.
 *
 * <p>Each time a parcel passes through an intermediate relay node, a per-hop fee
 * is charged. An optional inter-city fee is applied when the route crosses city boundaries.
 *
 * @author MANFOUO Braun
 */
@Value
@Jacksonized
@Builder
public class NetworkTransitRule {

    UUID id;

    /**
     * Maximum number of relay hops covered by this rule.
     * 0 or -1 means no limit.
     */
    int maxHops;

    /**
     * Fee charged per relay hop. In XAF.
     * Applied for each intermediate relay node the parcel passes through.
     */
    BigDecimal perHopFee;

    /**
     * Additional fee for handling at each node (scanning, transfer).
     * Added once per relay node, on top of the per-hop fee.
     */
    BigDecimal nodeHandlingFee;

    /**
     * Whether this rule is active only for inter-city routes
     * (routes that cross city/municipality boundaries).
     */
    boolean isInterCity;

    /**
     * Flat supplemental fee applied once when the route is inter-city.
     * Added in addition to the per-hop fees.
     * Null or 0 when not applicable.
     */
    BigDecimal interCityTransitFee;

    /**
     * Computes the total transit fee for the given number of relay hops.
     *
     * <p>Formula:
     * <pre>
     *   transitFee = hopCount × (perHopFee + nodeHandlingFee) + (isInterCity ? interCityTransitFee : 0)
     * </pre>
     *
     * @param hopCount  number of relay hops on the route
     * @param interCity whether the route is inter-city
     * @param currency  ISO 4217 currency code for the result
     * @return total transit fee
     */
    public Money computeFee(int hopCount, boolean interCity, String currency) {
        if (hopCount <= 0) return Money.of(BigDecimal.ZERO, currency);

        BigDecimal hopBase = (perHopFee != null ? perHopFee : BigDecimal.ZERO)
                .add(nodeHandlingFee != null ? nodeHandlingFee : BigDecimal.ZERO);

        BigDecimal total = hopBase.multiply(BigDecimal.valueOf(hopCount));

        if (interCity && interCityTransitFee != null
                && interCityTransitFee.compareTo(BigDecimal.ZERO) > 0) {
            total = total.add(interCityTransitFee);
        }
        return Money.of(total, currency);
    }

    /**
     * Returns {@code true} if this rule should be applied for the given route parameters.
     *
     * @param hopCount  number of relay hops
     * @param interCity whether the route crosses a city boundary
     * @return true when applicable
     */
    public boolean appliesTo(int hopCount, boolean interCity) {
        if (maxHops > 0 && hopCount > maxHops) return false;
        return !isInterCity || interCity;
    }
}
