package com.yowyob.tiibntick.core.billing.templates.adapter.outbound.persistence.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.billing.templates.adapter.outbound.persistence.entity.CustomPolicyTemplateEntity;
import com.yowyob.tiibntick.core.billing.templates.adapter.outbound.persistence.entity.PolicyTemplateEntity;
import com.yowyob.tiibntick.core.billing.templates.adapter.outbound.persistence.entity.TemplateParameterEntity;
import com.yowyob.tiibntick.core.billing.templates.domain.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Mapper component responsible for converting between persistence entities and
 * domain model objects for the billing templates module.
 *
 * <p>Uses Jackson {@link ObjectMapper} for JSON serialization/deserialization of
 * embedded collections (applicable types, parameters map).
 *
 * @author MANFOUO Braun
 * @version 1.0
 * @since 2026-05-26
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PolicyTemplateMapper {

    private final ObjectMapper objectMapper;

    // ─── PolicyTemplate: Entity → Domain ──────────────────────────────────

    /**
     * Converts a {@link PolicyTemplateEntity} and its associated parameter entities
     * to the domain {@link PolicyTemplate} aggregate.
     *
     * @param entity     the main template entity
     * @param paramEntities list of parameter entities belonging to this template
     * @return the domain PolicyTemplate
     */
    public PolicyTemplate toDomain(PolicyTemplateEntity entity, List<TemplateParameterEntity> paramEntities) {
        List<PolicyOwnerType> applicableTo = deserializeOwnerTypes(entity.getApplicableToJson());
        List<TemplateParameter> parameters = paramEntities.stream()
                .map(this::paramToDomain)
                .toList();

        return PolicyTemplate.builder()
                .id(entity.getId())
                .templateCode(entity.getTemplateCode())
                .name(entity.getName())
                .description(entity.getDescription())
                .category(TemplateCategory.valueOf(entity.getCategory()))
                .applicableTo(new ArrayList<>(applicableTo))
                .parameters(new ArrayList<>(parameters))
                .defaultDslRules(entity.getDefaultDslRules())
                .active(entity.isActive())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    /**
     * Converts a domain {@link PolicyTemplate} to a {@link PolicyTemplateEntity} for persistence.
     *
     * @param domain the domain template
     * @return the persistence entity
     */
    public PolicyTemplateEntity toEntity(PolicyTemplate domain) {
        return PolicyTemplateEntity.builder()
                .id(domain.getId())
                .templateCode(domain.getTemplateCode())
                .name(domain.getName())
                .description(domain.getDescription())
                .category(domain.getCategory().name())
                .applicableToJson(serializeOwnerTypes(domain.getApplicableTo()))
                .defaultDslRules(domain.getDefaultDslRules())
                .active(domain.isActive())
                .createdAt(domain.getCreatedAt())
                .updatedAt(domain.getUpdatedAt())
                .build();
    }

    /**
     * Converts a domain {@link TemplateParameter} to a {@link TemplateParameterEntity}.
     *
     * @param param     the domain parameter
     * @param templateId the parent template UUID
     * @return the persistence entity
     */
    public TemplateParameterEntity paramToEntity(TemplateParameter param, UUID templateId) {
        return TemplateParameterEntity.builder()
                .id(UUID.randomUUID())
                .templateId(templateId)
                .parameterKey(param.getKey())
                .labelFr(param.getLabelFr())
                .labelEn(param.getLabelEn())
                .defaultValue(param.getDefaultValue())
                .minValue(param.getMinValue())
                .maxValue(param.getMaxValue())
                .unit(param.getUnit())
                .parameterType(param.getType().name())
                .helpText(param.getHelpText())
                .build();
    }

    /**
     * Converts a {@link TemplateParameterEntity} to the domain {@link TemplateParameter} value object.
     *
     * @param entity the parameter entity
     * @return the domain value object
     */
    public TemplateParameter paramToDomain(TemplateParameterEntity entity) {
        return TemplateParameter.builder()
                .key(entity.getParameterKey())
                .labelFr(entity.getLabelFr())
                .labelEn(entity.getLabelEn())
                .defaultValue(entity.getDefaultValue())
                .minValue(entity.getMinValue())
                .maxValue(entity.getMaxValue())
                .unit(entity.getUnit())
                .type(ParameterType.valueOf(entity.getParameterType()))
                .helpText(entity.getHelpText())
                .build();
    }

    // ─── CustomPolicyTemplate: Entity → Domain ────────────────────────────

    /**
     * Converts a {@link CustomPolicyTemplateEntity} to the domain {@link CustomPolicyTemplate}.
     *
     * @param entity the custom template entity
     * @return the domain object
     */
    public CustomPolicyTemplate customToDomain(CustomPolicyTemplateEntity entity) {
        return CustomPolicyTemplate.builder()
                .id(entity.getId())
                .ownerActorId(entity.getOwnerActorId())
                .ownerType(PolicyOwnerType.valueOf(entity.getOwnerType()))
                .name(entity.getName())
                .sourceTemplateCode(entity.getSourceTemplateCode())
                .lastGeneratedPolicyId(entity.getLastGeneratedPolicyId())
                .customizedParameters(deserializeParamMap(entity.getCustomizedParametersJson()))
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    /**
     * Converts a domain {@link CustomPolicyTemplate} to a {@link CustomPolicyTemplateEntity}.
     *
     * @param domain the domain object
     * @return the persistence entity
     */
    public CustomPolicyTemplateEntity customToEntity(CustomPolicyTemplate domain) {
        return CustomPolicyTemplateEntity.builder()
                .id(domain.getId())
                .ownerActorId(domain.getOwnerActorId())
                .ownerType(domain.getOwnerType().name())
                .name(domain.getName())
                .sourceTemplateCode(domain.getSourceTemplateCode())
                .lastGeneratedPolicyId(domain.getLastGeneratedPolicyId())
                .customizedParametersJson(serializeParamMap(domain.getCustomizedParameters()))
                .createdAt(domain.getCreatedAt())
                .updatedAt(domain.getUpdatedAt())
                .build();
    }

    // ─── JSON helpers ──────────────────────────────────────────────────────

    private String serializeOwnerTypes(List<PolicyOwnerType> types) {
        try {
            List<String> names = types.stream().map(Enum::name).toList();
            return objectMapper.writeValueAsString(names);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize owner types: {}", types, e);
            return "[]";
        }
    }

    private List<PolicyOwnerType> deserializeOwnerTypes(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            List<String> names = objectMapper.readValue(json, new TypeReference<>() {});
            return names.stream().map(PolicyOwnerType::valueOf).toList();
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize owner types from JSON: {}", json, e);
            return List.of();
        }
    }

    private String serializeParamMap(Map<String, String> params) {
        if (params == null) return "{}";
        try {
            return objectMapper.writeValueAsString(params);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize parameter map", e);
            return "{}";
        }
    }

    private Map<String, String> deserializeParamMap(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize parameter map from JSON: {}", json, e);
            return Map.of();
        }
    }
}
