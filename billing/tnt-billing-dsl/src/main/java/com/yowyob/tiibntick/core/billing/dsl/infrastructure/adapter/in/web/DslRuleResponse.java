package com.yowyob.tiibntick.core.billing.dsl.infrastructure.adapter.in.web;

import com.yowyob.tiibntick.core.billing.dsl.domain.model.DslAction;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.DslRule;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Outbound REST DTO for a {@link DslRule}.
 *
 * @author MANFOUO Braun
 */
public record DslRuleResponse(
        UUID id,
        String name,
        String description,
        String conditionExpression,
        String actionExpression,
        List<DslAction> actions,
        int priority,
        boolean active,
        UUID tenantId,
        UUID policyId,
        Instant createdAt,
        Instant updatedAt
) {
    public static DslRuleResponse from(DslRule rule) {
        return new DslRuleResponse(
                rule.getId(),
                rule.getName(),
                rule.getDescription(),
                rule.getConditionExpression(),
                rule.getActionExpression(),
                rule.getActions(),
                rule.getPriority(),
                rule.isActive(),
                rule.getTenantId(),
                rule.getPolicyId(),
                rule.getCreatedAt(),
                rule.getUpdatedAt()
        );
    }
}
