package com.yowyob.tiibntick.core.billing.templates.adapter.inbound.web;

import com.yowyob.tiibntick.core.billing.templates.adapter.inbound.web.response.*;
import com.yowyob.tiibntick.core.billing.templates.domain.model.CustomPolicyTemplate;
import com.yowyob.tiibntick.core.billing.templates.domain.model.PolicyTemplate;
import com.yowyob.tiibntick.core.billing.templates.domain.model.TemplatePreviewResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Converts domain objects to REST response DTOs.
 *
 * @author MANFOUO Braun
 * @version 1.0
 * @since 2026-05-26
 */
@Component
public class ResponseMapper {

    /**
     * Converts a domain {@link PolicyTemplate} to {@link PolicyTemplateResponse}.
     */
    public PolicyTemplateResponse toResponse(PolicyTemplate template) {
        List<TemplateParameterResponse> params = template.getParameters().stream()
                .map(p -> TemplateParameterResponse.builder()
                        .key(p.getKey())
                        .labelFr(p.getLabelFr())
                        .labelEn(p.getLabelEn())
                        .defaultValue(p.getDefaultValue())
                        .minValue(p.getMinValue())
                        .maxValue(p.getMaxValue())
                        .unit(p.getUnit())
                        .type(p.getType().name())
                        .helpText(p.getHelpText())
                        .build())
                .toList();

        return PolicyTemplateResponse.builder()
                .id(template.getId())
                .templateCode(template.getTemplateCode())
                .name(template.getName())
                .description(template.getDescription())
                .category(template.getCategory().name())
                .applicableTo(template.getApplicableTo().stream().map(Enum::name).toList())
                .parameters(params)
                .defaultDslRules(template.getDefaultDslRules())
                .active(template.isActive())
                .createdAt(template.getCreatedAt())
                .updatedAt(template.getUpdatedAt())
                .build();
    }

    /**
     * Converts a domain {@link CustomPolicyTemplate} to {@link CustomPolicyTemplateResponse}.
     */
    public CustomPolicyTemplateResponse toResponse(CustomPolicyTemplate custom) {
        return CustomPolicyTemplateResponse.builder()
                .id(custom.getId())
                .ownerActorId(custom.getOwnerActorId())
                .ownerType(custom.getOwnerType().name())
                .name(custom.getName())
                .sourceTemplateCode(custom.getSourceTemplateCode())
                .lastGeneratedPolicyId(custom.getLastGeneratedPolicyId())
                .customizedParameters(custom.getCustomizedParameters())
                .createdAt(custom.getCreatedAt())
                .updatedAt(custom.getUpdatedAt())
                .build();
    }

    /**
     * Converts a domain {@link TemplatePreviewResult} to {@link PreviewPriceResponse}.
     */
    public PreviewPriceResponse toResponse(TemplatePreviewResult result) {
        List<PreviewPriceResponse.SurchargeItem> surcharges = result.getAppliedSurcharges().stream()
                .map(s -> PreviewPriceResponse.SurchargeItem.builder()
                        .code(s.getCode())
                        .labelFr(s.getLabelFr())
                        .labelEn(s.getLabelEn())
                        .amountXaf(s.getAmountXaf())
                        .unit(s.getUnit())
                        .build())
                .toList();

        TemplatePreviewResult.PreviewScenario sc = result.getScenario();
        Map<String, Object> scenarioMap = sc != null ? Map.of(
                "distanceKm", sc.getDistanceKm(),
                "weightKg", sc.getWeightKg(),
                "packageType", sc.getPackageType(),
                "priority", sc.getPriority(),
                "clientTransactionCount", sc.getClientTransactionCount(),
                "deliveryZoneType", sc.getDeliveryZoneType(),
                "weatherCondition", sc.getWeatherCondition(),
                "paymentMethod", sc.getPaymentMethod(),
                "timeOfDay", sc.getTimeOfDay(),
                "dayOfWeek", sc.getDayOfWeek()
        ) : Map.of();

        return PreviewPriceResponse.builder()
                .templateCode(result.getTemplateCode())
                .totalPriceXaf(result.getTotalPriceXaf())
                .basePriceXaf(result.getBasePriceXaf())
                .distanceCostXaf(result.getDistanceCostXaf())
                .weightCostXaf(result.getWeightCostXaf())
                .totalSurchargesXaf(result.getTotalSurchargesXaf())
                .appliedSurcharges(surcharges)
                .currency(result.getCurrency())
                .aboveMinimumPrice(result.isAboveMinimumPrice())
                .minimumPriceXaf(result.getMinimumPriceXaf())
                .scenario(scenarioMap)
                .build();
    }

    /**
     * Builds a successful ApplyTemplateResponse.
     */
    public ApplyTemplateResponse toApplyResponse(UUID policyId, String templateCode,
                                                  String policyName, boolean customSaved) {
        return ApplyTemplateResponse.builder()
                .createdPolicyId(policyId)
                .templateCode(templateCode)
                .policyName(policyName)
                .policyStatus("DRAFT")
                .customTemplateSaved(customSaved)
                .message("BillingPolicy successfully created in DRAFT state. Activate it to start using it.")
                .build();
    }
}
