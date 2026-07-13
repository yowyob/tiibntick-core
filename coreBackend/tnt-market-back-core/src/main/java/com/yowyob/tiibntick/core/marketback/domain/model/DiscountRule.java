package com.yowyob.tiibntick.core.marketback.domain.model;

/**
 * Value Object — discount rule inside a MarketCampaign.
 * @author MANFOUO Braun
 */
public record DiscountRule(
        DiscountType discountType,
        double value,
        long maxDiscountXaf,
        long minimumOrderXaf
) {
    public Money apply(Money original) {
        if (original.amount() < minimumOrderXaf) return original;
        return switch (discountType) {
            case PERCENTAGE -> {
                long disc = Math.round(original.amount() * value / 100.0);
                if (maxDiscountXaf > 0) disc = Math.min(disc, maxDiscountXaf);
                yield Money.ofXaf(Math.max(0, original.amount() - disc));
            }
            case FLAT_AMOUNT -> Money.ofXaf(Math.max(0, original.amount() - (long) value));
            case FREE_DELIVERY -> Money.zeroXaf();
        };
    }

    public String preview(Money original) {
        return "From " + original.formatted() + " → " + apply(original).formatted();
    }
}
