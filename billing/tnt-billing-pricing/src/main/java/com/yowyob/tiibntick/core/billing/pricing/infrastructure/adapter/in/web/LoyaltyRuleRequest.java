package com.yowyob.tiibntick.core.billing.pricing.infrastructure.adapter.in.web;

import jakarta.validation.constraints.Min;

import java.math.BigDecimal;

public record LoyaltyRuleRequest(
        @Min(1) Integer minimumTransactionCount,
        @Min(1) Integer periodDays,
        BigDecimal minimumTotalSpentXAF,
        BigDecimal discountPercentage
) {}
