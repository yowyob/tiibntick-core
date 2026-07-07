package com.yowyob.tiibntick.core.billing.pricing.infrastructure.adapter.in.web;

import com.yowyob.tiibntick.core.billing.pricing.domain.model.*;
import com.yowyob.tiibntick.core.billing.pricing.domain.model.enums.PolicyOwnerType;
import com.yowyob.tiibntick.core.billing.pricing.domain.model.enums.PolicyStatus;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.DslAccessLevel;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.Money;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Utility mapper — converts inbound REST DTOs to domain objects.
 *
 * <h3> additions</h3>
 * <p>Maps {@link CreateBillingPolicyRequest#ownerType}, {@link CreateBillingPolicyRequest#ownerActorId}
 * and {@link CreateBillingPolicyRequest#dslAccessLevel} to the domain.</p>
 *
 * @author MANFOUO Braun
 */
final class RequestToDomainMapper {

    private RequestToDomainMapper() {}

    static BillingPolicy toDomain(CreateBillingPolicyRequest req) {
        // Resolve DSL access level
        DslAccessLevel accessLevel = req.dslAccessLevel() != null
                ? req.dslAccessLevel()
                : resolveDslAccessLevel(req.ownerType());

        return BillingPolicy.builder()
                .id(UUID.randomUUID())
                .tenantId(req.tenantId())
                .agencyId(req.agencyId())
                .name(req.name())
                .description(req.description())
                .pricingRules(mapPricingRules(req.pricingRules()))
                .surchargeRules(mapSurchargeRules(req.surchargeRules()))
                .loyaltyRules(mapLoyaltyRules(req.loyaltyRules()))
                .promotions(List.of())
                .commissionRules(List.of())
                .specialSurcharges(List.of())
                .hubStorageRules(List.of())
                .networkTransitRules(List.of())
                .isDefault(Boolean.TRUE.equals(req.isDefault()))
                .status(PolicyStatus.DRAFT)
                .validFrom(req.validFrom() != null ? req.validFrom() : LocalDate.now())
                .validTo(req.validTo())
                //  — owner metadata
                .ownerType(req.ownerType())
                .ownerActorId(req.ownerActorId())
                .dslAccessLevel(accessLevel)
                .build();
    }

    private static List<PricingRule> mapPricingRules(List<PricingRuleRequest> reqs) {
        if (reqs == null) return List.of();
        return reqs.stream().map(r -> {
            String currency = r.currencyCode() != null ? r.currencyCode() : "XAF";
            return PricingRule.builder()
                    .id(UUID.randomUUID())
                    .name(r.name())
                    .conditionExpression(r.conditionExpression())
                    .basePrice(Money.of(r.basePriceAmount(), currency))
                    .perKmRate(r.perKmRateAmount() != null
                            ? Money.of(r.perKmRateAmount(), currency) : null)
                    .perKgRate(r.perKgRateAmount() != null
                            ? Money.of(r.perKgRateAmount(), currency) : null)
                    .minimumPrice(r.minimumPriceAmount() != null
                            ? Money.of(r.minimumPriceAmount(), currency) : null)
                    .maximumPrice(r.maximumPriceAmount() != null
                            ? Money.of(r.maximumPriceAmount(), currency) : null)
                    .priority(r.priority() != null ? r.priority() : 0)
                    .build();
        }).toList();
    }

    private static List<SurchargeRule> mapSurchargeRules(List<SurchargeRuleRequest> reqs) {
        if (reqs == null) return List.of();
        return reqs.stream().map(r -> SurchargeRule.builder()
                .id(UUID.randomUUID())
                .name(r.name())
                .conditionExpression(r.conditionExpression())
                .surchargeType(r.surchargeType())
                .value(r.value())
                .description(r.description())
                .build()).toList();
    }

    private static List<LoyaltyRule> mapLoyaltyRules(List<LoyaltyRuleRequest> reqs) {
        if (reqs == null) return List.of();
        return reqs.stream().map(r -> LoyaltyRule.builder()
                .id(UUID.randomUUID())
                .minimumTransactionCount(r.minimumTransactionCount() != null ? r.minimumTransactionCount() : 1)
                .periodDays(r.periodDays() != null ? r.periodDays() : 30)
                .minimumTotalSpentXAF(r.minimumTotalSpentXAF())
                .discountPercentage(r.discountPercentage())
                .build()).toList();
    }

    private static DslAccessLevel resolveDslAccessLevel(PolicyOwnerType ownerType) {
        if (ownerType == null) return DslAccessLevel.FULL;
        return switch (ownerType) {
            case AGENCY, ADMIN, MARKET -> DslAccessLevel.FULL;
            case FREELANCER_ORG, POINT, LINK -> DslAccessLevel.SIMPLIFIED;
        };
    }
}
