package com.yowyob.tiibntick.core.billing.pricing.application;

import com.yowyob.tiibntick.core.billing.pricing.application.service.CommissionCalculatorService;
import com.yowyob.tiibntick.core.billing.pricing.domain.model.BillingPolicy;
import com.yowyob.tiibntick.core.billing.pricing.domain.model.CommissionRule;
import com.yowyob.tiibntick.core.billing.pricing.domain.model.PlatformFeeRule;
import com.yowyob.tiibntick.core.billing.pricing.domain.model.PricingRule;
import com.yowyob.tiibntick.core.billing.pricing.domain.model.enums.CommissionAppliesTo;
import com.yowyob.tiibntick.core.billing.pricing.domain.model.enums.FeeType;
import com.yowyob.tiibntick.core.billing.pricing.domain.model.enums.PolicyStatus;
import com.yowyob.tiibntick.core.billing.pricing.domain.port.out.IBillingPolicyRepository;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.Money;
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
@DisplayName("CommissionCalculatorService — commission breakdown tests")
class CommissionCalculatorServiceTest {

    @Mock
    private IBillingPolicyRepository policyRepository;

    @InjectMocks
    private CommissionCalculatorService commissionService;

    private final UUID POLICY_ID = UUID.randomUUID();
    private final UUID TENANT_ID = UUID.randomUUID();

    private BillingPolicy policyWith(CommissionRule commissionRule, PlatformFeeRule feeRule) {
        return BillingPolicy.builder()
                .id(POLICY_ID).tenantId(TENANT_ID).name("Test")
                .pricingRules(List.of(PricingRule.builder().id(UUID.randomUUID())
                        .name("R").conditionExpression("weight <= 100")
                        .basePrice(Money.of(1000L, "XAF")).priority(10).build()))
                .surchargeRules(List.of()).promotions(List.of())
                .loyaltyRules(List.of())
                .commissionRules(commissionRule != null ? List.of(commissionRule) : List.of())
                .platformFeeRule(feeRule)
                .isDefault(false)
                .status(PolicyStatus.ACTIVE)
                .validFrom(LocalDate.now())
                .build();
    }

    @Test
    @DisplayName("PERCENTAGE commission: 20% deliverer, 5% platform on 2000 XAF")
    void testPercentageCommission() {
        CommissionRule rule = CommissionRule.builder()
                .id(UUID.randomUUID())
                .applyToType(CommissionAppliesTo.ALL)
                .delivererBaseCommissionPct(new BigDecimal("20"))
                .platformCommissionPct(new BigDecimal("5"))
                .bonusRules(List.of())
                .build();

        when(policyRepository.findById(POLICY_ID))
                .thenReturn(Mono.just(policyWith(rule, null)));

        Money selling = Money.of(2000L, "XAF");

        StepVerifier.create(commissionService.compute(POLICY_ID, selling, CommissionAppliesTo.PERMANENT))
                .expectNextMatches(bd -> {
                    // deliverer = 20% × 2000 = 400
                    boolean delivererOk = bd.delivererCommission().getAmount()
                            .compareTo(new BigDecimal("400")) == 0;
                    // platform = 5% × 2000 = 100
                    boolean platformOk = bd.platformFee().getAmount()
                            .compareTo(new BigDecimal("100")) == 0;
                    return delivererOk && platformOk;
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("FIXED platform fee overrides percentage when platformFeeRule is present")
    void testFixedPlatformFee() {
        PlatformFeeRule feeRule = PlatformFeeRule.builder()
                .feeType(FeeType.FIXED)
                .feeValue(new BigDecimal("300"))
                .minimumFee(Money.of(200L, "XAF"))
                .build();

        when(policyRepository.findById(POLICY_ID))
                .thenReturn(Mono.just(policyWith(null, feeRule)));

        Money selling = Money.of(2000L, "XAF");

        StepVerifier.create(commissionService.compute(POLICY_ID, selling, CommissionAppliesTo.ALL))
                .expectNextMatches(bd ->
                        bd.platformFee().getAmount().compareTo(new BigDecimal("300")) == 0)
                .verifyComplete();
    }

    @Test
    @DisplayName("FREELANCER rule should not apply to PERMANENT deliverer")
    void testFreelancerRuleNotAppliedToPermanent() {
        CommissionRule freelancerRule = CommissionRule.builder()
                .id(UUID.randomUUID())
                .applyToType(CommissionAppliesTo.FREELANCER)
                .delivererBaseCommissionPct(new BigDecimal("30"))
                .platformCommissionPct(new BigDecimal("10"))
                .bonusRules(List.of())
                .build();

        when(policyRepository.findById(POLICY_ID))
                .thenReturn(Mono.just(policyWith(freelancerRule, null)));

        Money selling = Money.of(2000L, "XAF");

        StepVerifier.create(commissionService.compute(POLICY_ID, selling, CommissionAppliesTo.PERMANENT))
                .expectNextMatches(bd ->
                        bd.delivererCommission().getAmount().compareTo(BigDecimal.ZERO) == 0)
                .verifyComplete();
    }
}
