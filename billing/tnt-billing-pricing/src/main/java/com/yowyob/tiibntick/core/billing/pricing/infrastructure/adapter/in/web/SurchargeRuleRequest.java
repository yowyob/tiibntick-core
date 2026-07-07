package com.yowyob.tiibntick.core.billing.pricing.infrastructure.adapter.in.web;

import com.yowyob.tiibntick.core.billing.pricing.domain.model.enums.SurchargeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record SurchargeRuleRequest(
        @NotBlank String name,
        @NotBlank String conditionExpression,
        @NotNull SurchargeType surchargeType,
        @NotNull BigDecimal value,
        String description
) {}
