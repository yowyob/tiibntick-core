package com.yowyob.tiibntick.core.billing.pricing.infrastructure.adapter.in.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record PricingRuleRequest(
        @NotBlank String name,
        @NotBlank String conditionExpression,
        @NotNull BigDecimal basePriceAmount,
        String currencyCode,
        BigDecimal perKmRateAmount,
        BigDecimal perKgRateAmount,
        BigDecimal minimumPriceAmount,
        BigDecimal maximumPriceAmount,
        Integer priority
) {}
