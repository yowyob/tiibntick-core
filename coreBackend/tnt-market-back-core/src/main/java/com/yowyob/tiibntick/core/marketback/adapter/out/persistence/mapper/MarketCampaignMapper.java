package com.yowyob.tiibntick.core.marketback.adapter.out.persistence.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.marketback.adapter.out.persistence.entity.MarketCampaignEntity;
import com.yowyob.tiibntick.core.marketback.domain.model.CampaignId;
import com.yowyob.tiibntick.core.marketback.domain.model.CampaignScope;
import com.yowyob.tiibntick.core.marketback.domain.model.CampaignStatus;
import com.yowyob.tiibntick.core.marketback.domain.model.CampaignType;
import com.yowyob.tiibntick.core.marketback.domain.model.DiscountRule;
import com.yowyob.tiibntick.core.marketback.domain.model.DiscountType;
import com.yowyob.tiibntick.core.marketback.domain.model.MarketCampaign;
import com.yowyob.tiibntick.core.marketback.domain.model.MarketListingId;
import com.yowyob.tiibntick.core.marketback.domain.model.Money;
import com.yowyob.tiibntick.core.marketback.domain.model.ProviderType;
import com.yowyob.tiibntick.core.marketback.domain.model.ServiceType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Maps between the MarketCampaign aggregate and its R2DBC entity.
 * The {@link CampaignScope} value object (target listing ids / service types /
 * provider types) is persisted as three JSON array columns.
 *
 * <p>Note (ported as-is from tiibntick-market-backend): {@link MarketCampaign#reconstitute}
 * does not restore {@code promoCode}, {@code usageCount} or {@code maxUsage} onto the
 * rehydrated aggregate — those fields are only meaningful on a freshly {@link MarketCampaign#create}d
 * instance. The DB columns are still persisted correctly and promo-code lookup/validation
 * works via {@link com.yowyob.tiibntick.core.marketback.application.port.out.IMarketCampaignRepository#findByPromoCode}
 * (a SQL-level query), but a campaign reloaded from storage will report {@code usageCount=0}
 * and {@code promoCode=null} via its getters. This mirrors the original codebase's behavior
 * and is a domain-layer gap outside the scope of this persistence adapter.
 *
 * @author MANFOUO Braun
 */
@Component
public class MarketCampaignMapper {

    private final ObjectMapper objectMapper;

    public MarketCampaignMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public MarketCampaignEntity toEntity(MarketCampaign campaign, boolean isNew) {
        MarketCampaignEntity.MarketCampaignEntityBuilder builder = MarketCampaignEntity.builder()
                .id(campaign.getId().value())
                .isNew(isNew)
                .tenantId(campaign.getTenantId())
                .name(campaign.getName())
                .description(campaign.getDescription())
                .campaignType(campaign.getCampaignType().name())
                .status(campaign.getStatus().name())
                .promoCode(campaign.getPromoCode())
                .startDate(campaign.getStartDate())
                .endDate(campaign.getEndDate())
                .createdAt(campaign.getCreatedAt())
                .updatedAt(campaign.getUpdatedAt());

        DiscountRule discount = campaign.getDiscountRule();
        if (discount != null) {
            builder.discountType(discount.discountType().name())
                    .discountValue(BigDecimal.valueOf(discount.value()))
                    .maxDiscount(BigDecimal.valueOf(discount.maxDiscountXaf()))
                    .minOrderAmount(BigDecimal.valueOf(discount.minimumOrderXaf()));
        }

        CampaignScope scope = campaign.getScope();
        String currency = null;
        if (scope != null) {
            List<UUID> targetListingUuids = scope.targetListingIds() != null
                    ? scope.targetListingIds().stream().map(MarketListingId::value).toList() : null;
            builder.scopeProviderIds(toJson(targetListingUuids))
                    .scopeServiceTypes(toJson(scope.targetServiceTypes()))
                    .scopeCities(toJson(scope.targetProviderTypes()))
                    .applicableToAll(scope.applyToAll());
        }

        Money budget = campaign.getBudget();
        Money spentAmount = campaign.getSpentAmount();
        builder.budget(budget != null ? BigDecimal.valueOf(budget.amount()) : null);
        builder.spentAmount(spentAmount != null ? BigDecimal.valueOf(spentAmount.amount()) : null);
        currency = budget != null ? budget.currency() : (spentAmount != null ? spentAmount.currency() : null);
        builder.currency(currency);

        return builder.build();
    }

    public MarketCampaign toDomain(MarketCampaignEntity entity) {
        DiscountRule discount = new DiscountRule(
                DiscountType.valueOf(entity.getDiscountType()),
                entity.getDiscountValue() != null ? entity.getDiscountValue().doubleValue() : 0.0,
                entity.getMaxDiscount() != null ? entity.getMaxDiscount().longValue() : 0L,
                entity.getMinOrderAmount() != null ? entity.getMinOrderAmount().longValue() : 0L);

        List<UUID> targetListingUuids = fromJson(entity.getScopeProviderIds(), UUID.class);
        List<MarketListingId> targetListingIds = targetListingUuids.stream().map(MarketListingId::new).toList();
        List<ServiceType> serviceTypes = fromJson(entity.getScopeServiceTypes(), ServiceType.class);
        List<ProviderType> providerTypes = fromJson(entity.getScopeCities(), ProviderType.class);
        CampaignScope scope = new CampaignScope(entity.isApplicableToAll(), targetListingIds, serviceTypes, providerTypes);

        Money budget = entity.getBudget() != null ? new Money(entity.getBudget().longValue(), entity.getCurrency()) : null;
        Money spentAmount = entity.getSpentAmount() != null
                ? new Money(entity.getSpentAmount().longValue(), entity.getCurrency()) : null;

        return MarketCampaign.reconstitute(
                CampaignId.of(entity.getId()), entity.getTenantId(), entity.getName(), entity.getDescription(),
                CampaignType.valueOf(entity.getCampaignType()), CampaignStatus.valueOf(entity.getStatus()),
                discount, scope, budget, spentAmount,
                entity.getStartDate(), entity.getEndDate(), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    private String toJson(Object value) {
        if (value == null) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "[]";
        }
    }

    private <T> List<T> fromJson(String json, Class<T> clazz) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, clazz));
        } catch (Exception ex) {
            return Collections.emptyList();
        }
    }
}
