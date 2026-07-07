package com.yowyob.tiibntick.core.billing.dsl.infrastructure.adapter.in.web;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Inbound REST DTO for creating or updating a {@code DslRule}.
 *
 * @author MANFOUO Braun
 */
public record DslRuleRequest(

        @NotBlank(message = "Rule name is required")
        String name,

        String description,

        @NotBlank(message = "Condition expression is required")
        String conditionExpression,

        @NotBlank(message = "Action expression is required")
        String actionExpression,

        @Min(value = 0, message = "Priority must be >= 0")
        Integer priority,

        Boolean active,

        @NotNull(message = "tenantId is required")
        UUID tenantId,

        @NotNull(message = "policyId is required")
        UUID policyId
) {}
