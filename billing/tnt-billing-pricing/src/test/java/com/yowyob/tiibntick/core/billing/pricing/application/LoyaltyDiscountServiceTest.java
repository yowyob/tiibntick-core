package com.yowyob.tiibntick.core.billing.pricing.application;

import com.yowyob.tiibntick.core.billing.pricing.application.service.LoyaltyDiscountService;
import com.yowyob.tiibntick.core.billing.pricing.domain.exception.BillingPolicyNotFoundException;
import com.yowyob.tiibntick.core.billing.pricing.domain.model.BillingPolicy;
import com.yowyob.tiibntick.core.billing.pricing.domain.model.LoyaltyRule;
import com.yowyob.tiibntick.core.billing.pricing.domain.model.PricingRule;
import com.yowyob.tiibntick.core.billing.pricing.domain.model.enums.PolicyStatus;
import com.yowyob.tiibntick.core.billing.pricing.domain.port.out.IBillingPolicyRepository;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.Money;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.PricingContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("LoyaltyDiscountService")
class LoyaltyDiscountServiceTest {

    @Mock
    private IBillingPolicyRepository policyRepository;

    @InjectMocks
    private LoyaltyDiscountService loyaltyDiscountService;

    private final UUID POLICY_ID = UUID.randomUUID();
    private final UUID TENANT_ID = UUID.randomUUID();

    private BillingPolicy policyWithLoyalty(LoyaltyRule rule) {
        return BillingPolicy.builder()
                .id(POLICY_ID).tenantId(TENANT_ID).name("Test")
                .pricingRules(List.of(PricingRule.builder().id(UUID.randomUUID())
                        .name("R").conditionExpression("weight <= 100")
                        .basePrice(Money.of(1000L, "XAF")).priority(10).build()))
                .surchargeRules(List.of()).promotions(List.of())
                .loyaltyRules(rule != null ? List.of(rule) : List.of())
                .commissionRules(List.of())
                .isDefault(false)
                .status(PolicyStatus.ACTIVE)
                .validFrom(LocalDate.now())
                .build();
    }

    private PricingContext ctxFor(UUID tenantId, int clientTxCount) {
        return PricingContext.builder()
                .weightKg(3.0).distanceKm(5.0)
                .tenantId(tenantId)
                .clientTxCount(clientTxCount)
                .build();
    }

    @Test
    @DisplayName("getEligibleDiscountPct returns the rule's percentage when eligible")
    void testEligibleDiscount() {
        LoyaltyRule rule = LoyaltyRule.builder()
                .id(UUID.randomUUID())
                .minimumTransactionCount(10)
                .periodDays(90)
                .discountPercentage(new BigDecimal("5"))
                .build();

        when(policyRepository.findByIdAndTenantId(POLICY_ID, TENANT_ID))
                .thenReturn(Mono.just(policyWithLoyalty(rule)));

        StepVerifier.create(loyaltyDiscountService.getEligibleDiscountPct(POLICY_ID, ctxFor(TENANT_ID, 15)))
                .expectNextMatches(pct -> pct.compareTo(new BigDecimal("5")) == 0)
                .verifyComplete();
    }

    @Test
    @DisplayName("IDOR (Audit n°7 · #5): getEligibleDiscountPct must not reveal another tenant's loyalty rules")
    void testGetEligibleDiscountPctRejectsCrossTenantAccess() {
        UUID otherTenantId = UUID.randomUUID();

        // Policy belongs to TENANT_ID; caller's context carries otherTenantId.
        // findByIdAndTenantId(POLICY_ID, otherTenantId) must come back empty rather than
        // falling back to an unscoped read.
        when(policyRepository.findByIdAndTenantId(POLICY_ID, otherTenantId)).thenReturn(Mono.empty());

        StepVerifier.create(loyaltyDiscountService.getEligibleDiscountPct(
                        POLICY_ID, ctxFor(otherTenantId, 15)))
                .expectError(BillingPolicyNotFoundException.class)
                .verify();
    }

    @Test
    @DisplayName("IDOR (Audit n°7 · #5): computeDiscount must not reveal another tenant's loyalty rules")
    void testComputeDiscountRejectsCrossTenantAccess() {
        UUID otherTenantId = UUID.randomUUID();
        when(policyRepository.findByIdAndTenantId(POLICY_ID, otherTenantId)).thenReturn(Mono.empty());

        StepVerifier.create(loyaltyDiscountService.computeDiscount(
                        POLICY_ID, Money.of(2000L, "XAF"), ctxFor(otherTenantId, 15)))
                .expectError(BillingPolicyNotFoundException.class)
                .verify();
    }
}
