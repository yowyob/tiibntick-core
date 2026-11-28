package com.yowyob.tiibntick.core.billing.wallet.domain.policy;

import com.yowyob.tiibntick.core.billing.wallet.domain.model.EscrowShares;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

/**
 * Split of a delivery price (spec §4.1, pure domain): {@code PLATFORM = price × rate} rounded half-up to the unit
 * (XAF has no decimals), {@code MERCHANT} parts as given (QO-19: relay storage fee, agency share — none until their
 * amounts are set), {@code COURIER = price − PLATFORM − MERCHANT}. Refused when the courier part would be negative.
 */
public final class EscrowSharesPolicy {

    private EscrowSharesPolicy() {
    }

    public static EscrowShares split(BigDecimal price, BigDecimal rate, List<EscrowShares.Part> merchants) {
        Objects.requireNonNull(price, "price");
        Objects.requireNonNull(rate, "rate");
        if (price.signum() <= 0) {
            throw new IllegalArgumentException("An escrowed price must be positive: " + price);
        }
        if (rate.signum() < 0 || rate.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("A platform rate is between 0 and 1: " + rate);
        }
        BigDecimal unitPrice = price.setScale(0, RoundingMode.HALF_UP);
        BigDecimal platform = unitPrice.multiply(rate).setScale(0, RoundingMode.HALF_UP);
        EscrowShares partial = new EscrowShares(unitPrice, platform, merchants, null);
        BigDecimal courier = unitPrice.subtract(platform).subtract(partial.merchantTotal());
        if (courier.signum() < 0) {
            throw new IllegalArgumentException("The platform and merchant parts exceed the price " + unitPrice);
        }
        return new EscrowShares(unitPrice, platform, merchants, courier);
    }
}
