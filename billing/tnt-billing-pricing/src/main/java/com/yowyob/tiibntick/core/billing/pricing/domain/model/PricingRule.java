package com.yowyob.tiibntick.core.billing.pricing.domain.model;

import com.yowyob.tiibntick.core.billing.dsl.domain.model.Money;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.PricingContext;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.UUID;

@Value
@Builder
@Jacksonized
public class PricingRule {

    UUID id;
    String name;
    String conditionExpression;
    Money basePrice;
    Money perKmRate;
    Money perKgRate;
    Money minimumPrice;
    Money maximumPrice;
    int priority;

    public Money computeSubtotal(PricingContext ctx) {
        //String currency = basePrice.getCurrency().getCurrencyCode();
        Money subtotal = basePrice;

        if (perKmRate != null) {
            subtotal = subtotal.add(perKmRate.multiply(ctx.getDistanceKm()));
        }
        if (perKgRate != null) {
            subtotal = subtotal.add(perKgRate.multiply(ctx.getWeightKg()));
        }
        if (minimumPrice != null && subtotal.getAmount().compareTo(minimumPrice.getAmount()) < 0) {
            subtotal = minimumPrice;
        }
        if (maximumPrice != null && subtotal.getAmount().compareTo(maximumPrice.getAmount()) > 0) {
            subtotal = maximumPrice;
        }
        return subtotal;
    }

    public boolean hasPerKmRate() {
        return perKmRate != null;
    }

    public boolean hasPerKgRate() {
        return perKgRate != null;
    }
}
