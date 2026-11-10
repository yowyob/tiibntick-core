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
    /** Price per cubic metre of declared volume; {@code null} = volume is not billed. */
    Money perCbmRate;
    /** Price per minute of waiting at pickup/delivery; {@code null} = waiting is free. */
    Money waitingFeePerMinute;
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
        if (perCbmRate != null && ctx.getVolumeCbm() != null && ctx.getVolumeCbm() > 0) {
            subtotal = subtotal.add(perCbmRate.multiply(ctx.getVolumeCbm()));
        }
        if (waitingFeePerMinute != null && ctx.getWaitingMinutes() != null && ctx.getWaitingMinutes() > 0) {
            subtotal = subtotal.add(waitingFeePerMinute.multiply(ctx.getWaitingMinutes()));
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
