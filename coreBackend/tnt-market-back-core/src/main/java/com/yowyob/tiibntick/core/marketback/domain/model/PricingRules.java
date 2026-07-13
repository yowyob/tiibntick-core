package com.yowyob.tiibntick.core.marketback.domain.model;

/**
 * Value Object — pricing configuration of a ServiceOffer.
 * @author MANFOUO Braun
 */
public record PricingRules(
        Money basePrice,
        Money perKmRate,
        Money perKgRate,
        Money minimumPrice,
        Money maximumPrice,
        String currency,
        String pricingDslExpression
) {
    /** Naive formula-based estimate; DSL override handled by billing-core. */
    public Money estimate(PricingContext ctx) {
        long base = basePrice.amount();
        long km   = Math.round(perKmRate.amount() * ctx.distanceKm());
        long kg   = Math.round(perKgRate.amount() * ctx.weightKg());
        long total = base + km + kg;
        if (ctx.fragile())  total = Math.round(total * 1.15);
        if (ctx.express())  total = Math.round(total * 1.30);
        if (ctx.sameDay())  total = Math.round(total * 1.50);
        total = Math.max(total, minimumPrice.amount());
        if (maximumPrice != null && total > maximumPrice.amount()) total = maximumPrice.amount();
        return Money.ofXaf(total);
    }
}
