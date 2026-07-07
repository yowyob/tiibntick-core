package com.yowyob.tiibntick.core.billing.pricing.infrastructure.persistence.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.billing.pricing.domain.model.*;
import com.yowyob.tiibntick.core.billing.pricing.domain.model.enums.PolicyOwnerType;
import com.yowyob.tiibntick.core.billing.pricing.infrastructure.persistence.entity.BillingPolicyEntity;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.DslAccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Manual mapper between the {@link BillingPolicy} domain aggregate
 * and the {@link BillingPolicyEntity} R2DBC entity.
 *
 * <h3> additions</h3>
 * <p>Maps the new owner metadata and advanced rule list fields:
 * ownerType, ownerActorId, isFromTemplate, templateCode, dslAccessLevel,
 * specialSurcharges, hubStorageRules, networkTransitRules, fleetCostParameters.</p>
 *
 * @author MANFOUO Braun
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BillingPolicyMapper {

    private final ObjectMapper objectMapper;

    public BillingPolicyEntity toEntity(BillingPolicy domain) {
        return BillingPolicyEntity.builder()
                .id(domain.getId())
                .tenantId(domain.getTenantId())
                .agencyId(domain.getAgencyId())
                .name(domain.getName())
                .description(domain.getDescription())
                .pricingRulesJson(toJson(domain.getPricingRules()))
                .surchargeRulesJson(toJson(domain.getSurchargeRules()))
                .promotionsJson(toJson(domain.getPromotions()))
                .loyaltyRulesJson(toJson(domain.getLoyaltyRules()))
                .commissionRulesJson(toJson(domain.getCommissionRules()))
                .platformFeeRuleJson(toJson(domain.getPlatformFeeRule()))
                .isDefault(domain.isDefault())
                .status(domain.getStatus())
                .validFrom(domain.getValidFrom())
                .validTo(domain.getValidTo())
                .createdAt(domain.getCreatedAt())
                .updatedAt(domain.getUpdatedAt())
                //  — owner metadata
                .ownerType(domain.getOwnerType() != null ? domain.getOwnerType().name() : null)
                .ownerActorId(domain.getOwnerActorId())
                .isFromTemplate(domain.isFromTemplate())
                .templateCode(domain.getTemplateCode())
                .dslAccessLevel(domain.getDslAccessLevel() != null
                        ? domain.getDslAccessLevel().name() : null)
                //  — advanced rule lists
                .specialSurchargesJson(toJson(domain.getSpecialSurcharges()))
                .hubStorageRulesJson(toJson(domain.getHubStorageRules()))
                .networkTransitRulesJson(toJson(domain.getNetworkTransitRules()))
                .fleetCostParametersJson(toJson(domain.getFleetCostParameters()))
                .build();
    }

    public BillingPolicy toDomain(BillingPolicyEntity entity) {
        return BillingPolicy.builder()
                .id(entity.getId())
                .tenantId(entity.getTenantId())
                .agencyId(entity.getAgencyId())
                .name(entity.getName())
                .description(entity.getDescription())
                .pricingRules(fromJson(entity.getPricingRulesJson(),
                        new TypeReference<List<PricingRule>>() {}))
                .surchargeRules(fromJson(entity.getSurchargeRulesJson(),
                        new TypeReference<List<SurchargeRule>>() {}))
                .promotions(fromJson(entity.getPromotionsJson(),
                        new TypeReference<List<Promotion>>() {}))
                .loyaltyRules(fromJson(entity.getLoyaltyRulesJson(),
                        new TypeReference<List<LoyaltyRule>>() {}))
                .commissionRules(fromJson(entity.getCommissionRulesJson(),
                        new TypeReference<List<CommissionRule>>() {}))
                .platformFeeRule(fromJson(entity.getPlatformFeeRuleJson(), PlatformFeeRule.class))
                .isDefault(entity.isDefault())
                .status(entity.getStatus())
                .validFrom(entity.getValidFrom())
                .validTo(entity.getValidTo())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                //  — owner metadata
                .ownerType(entity.getOwnerType() != null
                        ? PolicyOwnerType.valueOf(entity.getOwnerType()) : null)
                .ownerActorId(entity.getOwnerActorId())
                .isFromTemplate(entity.isFromTemplate())
                .templateCode(entity.getTemplateCode())
                .dslAccessLevel(entity.getDslAccessLevel() != null
                        ? DslAccessLevel.valueOf(entity.getDslAccessLevel()) : null)
                //  — advanced rule lists
                .specialSurcharges(fromJson(entity.getSpecialSurchargesJson(),
                        new TypeReference<List<SpecialSurchargeRule>>() {}))
                .hubStorageRules(fromJson(entity.getHubStorageRulesJson(),
                        new TypeReference<List<HubStorageRule>>() {}))
                .networkTransitRules(fromJson(entity.getNetworkTransitRulesJson(),
                        new TypeReference<List<NetworkTransitRule>>() {}))
                .fleetCostParameters(fromJson(entity.getFleetCostParametersJson(),
                        FleetCostParameters.class))
                .build();
    }

    private String toJson(Object obj) {
        if (obj == null) return null;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("Serialization error: {}", e.getMessage());
            return null;
        }
    }

    private <T> T fromJson(String json, TypeReference<T> type) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            log.error("Deserialization error for type {}: {}", type.getType(), e.getMessage());
            return null;
        }
    }

    private <T> T fromJson(String json, Class<T> type) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            log.error("Deserialization error for class {}: {}", type.getSimpleName(), e.getMessage());
            return null;
        }
    }
}
