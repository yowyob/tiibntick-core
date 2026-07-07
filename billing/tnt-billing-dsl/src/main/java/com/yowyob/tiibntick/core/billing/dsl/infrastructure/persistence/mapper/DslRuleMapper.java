package com.yowyob.tiibntick.core.billing.dsl.infrastructure.persistence.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.DslAction;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.DslRule;
import com.yowyob.tiibntick.core.billing.dsl.infrastructure.persistence.entity.DslRuleEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Manual mapper between the {@link DslRule} domain model and the
 * {@link DslRuleEntity} R2DBC entity.
 * <p>
 * Actions are serialised to JSON for storage and deserialised back on load.
 * </p>
 *
 * @author MANFOUO Braun
 */
@Component
@RequiredArgsConstructor
public class DslRuleMapper {

    private final ObjectMapper objectMapper;

    public DslRuleEntity toEntity(DslRule rule) {
        return DslRuleEntity.builder()
                .id(rule.getId())
                .name(rule.getName())
                .description(rule.getDescription())
                .conditionExpression(rule.getConditionExpression())
                .actionExpression(rule.getActionExpression())
                .actionsJson(serialiseActions(rule.getActions()))
                .priority(rule.getPriority())
                .active(rule.isActive())
                .tenantId(rule.getTenantId())
                .policyId(rule.getPolicyId())
                .createdAt(rule.getCreatedAt())
                .updatedAt(rule.getUpdatedAt())
                .build();
    }

    public DslRule toDomain(DslRuleEntity entity) {
        return DslRule.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .conditionExpression(entity.getConditionExpression())
                .actionExpression(entity.getActionExpression())
                .actions(deserialiseActions(entity.getActionsJson()))
                .priority(entity.getPriority())
                .active(entity.isActive())
                .tenantId(entity.getTenantId())
                .policyId(entity.getPolicyId())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private String serialiseActions(List<DslAction> actions) {
        if (actions == null || actions.isEmpty()) return "[]";
        try {
            return objectMapper.writeValueAsString(actions);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialise DslAction list", e);
        }
    }

    private List<DslAction> deserialiseActions(String json) {
        if (json == null || json.isBlank() || json.equals("[]")) return List.of();
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, DslAction.class));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialise DslAction list", e);
        }
    }
}
