package com.yowyob.tiibntick.core.billing.pricing.application;

import com.yowyob.tiibntick.core.billing.pricing.application.service.PricingEngineService;
import com.yowyob.tiibntick.core.billing.pricing.domain.exception.NoPricingRuleMatchException;
import com.yowyob.tiibntick.core.billing.pricing.domain.exception.PolicyNotActiveException;
import com.yowyob.tiibntick.core.billing.pricing.domain.model.*;
import com.yowyob.tiibntick.core.billing.pricing.domain.model.enums.*;
import com.yowyob.tiibntick.core.billing.pricing.domain.port.out.IBillingPolicyRepository;
import com.yowyob.tiibntick.core.billing.dsl.application.service.DslCompilerService;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.*;
import com.yowyob.tiibntick.core.billing.dsl.infrastructure.dsl.evaluator.DslEvaluator;
import com.yowyob.tiibntick.core.billing.dsl.infrastructure.dsl.lexer.DslLexer;
import com.yowyob.tiibntick.core.billing.dsl.infrastructure.dsl.parser.DslParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PricingEngineService — full price computation pipeline")
class PricingEngineServiceTest {

    @Mock
    private IBillingPolicyRepository policyRepository;

    private PricingEngineService pricingEngineService;

    private final UUID TENANT_ID = UUID.randomUUID();
    private final UUID POLICY_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        DslLexer lexer = new DslLexer();
        DslParser parser = new DslParser();
        DslCompilerService compiler = new DslCompilerService(lexer, parser);
        DslEvaluator evaluator = new DslEvaluator();
        pricingEngineService = new PricingEngineService(policyRepository, compiler, evaluator);
    }

    private PricingContext ydeCtx() {
        return PricingContext.builder()
                .weightKg(3.2)
                .distanceKm(8.5)
                .packageTypes(List.of(PackageType.FRAGILE))
                .priority(DeliveryPriority.HIGH)
                .clientTxCount(15)
                .timeOfDay(LocalTime.of(8, 30))
                .weatherCondition(WeatherCondition.RAIN_LIGHT)
                .roadType(RoadType.PAVED)
                .tenantId(TENANT_ID)
                .build();
    }

    private BillingPolicy activePolicy(List<PricingRule> rules,
                                        List<SurchargeRule> surcharges,
                                        List<LoyaltyRule> loyaltyRules) {
        return BillingPolicy.builder()
                .id(POLICY_ID)
                .tenantId(TENANT_ID)
                .name("Standard YDE")
                .pricingRules(rules)
                .surchargeRules(surcharges != null ? surcharges : List.of())
                .promotions(List.of())
                .loyaltyRules(loyaltyRules != null ? loyaltyRules : List.of())
                .commissionRules(List.of())
                .isDefault(true)
                .status(com.yowyob.tiibntick.core.billing.pricing.domain.model.enums.PolicyStatus.ACTIVE)
                .validFrom(LocalDate.now())
                .build();
    }

    @Test
    @DisplayName("base rule: weight<=5 AND distance<=10 → 1000 + 8.5×50 = 1425 XAF")
    void testBaseRule() {
        PricingRule rule = PricingRule.builder()
                .id(UUID.randomUUID())
                .name("Standard YDE")
                .conditionExpression("weight <= 5 AND distance <= 10")
                .basePrice(Money.of(1000L, "XAF"))
                .perKmRate(Money.of(50L, "XAF"))
                .priority(10)
                .build();

        when(policyRepository.findByIdAndTenantId(POLICY_ID, TENANT_ID))
                .thenReturn(Mono.just(activePolicy(List.of(rule), null, null)));

        StepVerifier.create(pricingEngineService.evaluatePolicy(POLICY_ID, ydeCtx()))
                .expectNextMatches(eval ->
                        eval.getSellingPrice().getAmount().compareTo(new BigDecimal("1425")) == 0)
                .verifyComplete();
    }

    @Test
    @DisplayName("fragile surcharge +500 XAF → total 1925 XAF")
    void testFragileSurcharge() {
        PricingRule rule = PricingRule.builder()
                .id(UUID.randomUUID()).name("Standard")
                .conditionExpression("weight <= 5 AND distance <= 10")
                .basePrice(Money.of(1000L, "XAF")).perKmRate(Money.of(50L, "XAF"))
                .priority(10).build();

        SurchargeRule fragile = SurchargeRule.builder()
                .id(UUID.randomUUID()).name("Fragile")
                .conditionExpression("packageType == 'FRAGILE'")
                .surchargeType(SurchargeType.FIXED)
                .value(new BigDecimal("500")).build();

        when(policyRepository.findByIdAndTenantId(POLICY_ID, TENANT_ID))
                .thenReturn(Mono.just(activePolicy(List.of(rule), List.of(fragile), null)));

        StepVerifier.create(pricingEngineService.evaluatePolicy(POLICY_ID, ydeCtx()))
                .expectNextMatches(eval ->
                        eval.getSellingPrice().getAmount().compareTo(new BigDecimal("1925")) == 0)
                .verifyComplete();
    }

    @Test
    @DisplayName("percentage surcharge +15% for HIGH priority → 1925 + 289 = 2214 XAF")
    void testPercentageSurcharge() {
        PricingRule rule = PricingRule.builder()
                .id(UUID.randomUUID()).name("Standard")
                .conditionExpression("weight <= 5 AND distance <= 10")
                .basePrice(Money.of(1000L, "XAF")).perKmRate(Money.of(50L, "XAF"))
                .priority(10).build();

        SurchargeRule fragile = SurchargeRule.builder()
                .id(UUID.randomUUID()).name("Fragile")
                .conditionExpression("packageType == 'FRAGILE'")
                .surchargeType(SurchargeType.FIXED).value(new BigDecimal("500")).build();

        SurchargeRule highPriority = SurchargeRule.builder()
                .id(UUID.randomUUID()).name("High Priority")
                .conditionExpression("priority == 'HIGH'")
                .surchargeType(SurchargeType.PERCENTAGE).value(new BigDecimal("15")).build();

        when(policyRepository.findByIdAndTenantId(POLICY_ID, TENANT_ID))
                .thenReturn(Mono.just(activePolicy(List.of(rule), List.of(fragile, highPriority), null)));

        StepVerifier.create(pricingEngineService.evaluatePolicy(POLICY_ID, ydeCtx()))
                .expectNextMatches(eval -> {
                    BigDecimal price = eval.getSellingPrice().getAmount();
                    return price.compareTo(new BigDecimal("2213")) == 0
                            || price.compareTo(new BigDecimal("2214")) == 0;
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("loyalty discount -5% for txCount >= 10 → reduces final price")
    void testLoyaltyDiscount() {
        PricingRule rule = PricingRule.builder()
                .id(UUID.randomUUID()).name("Standard")
                .conditionExpression("weight <= 5 AND distance <= 10")
                .basePrice(Money.of(1000L, "XAF")).perKmRate(Money.of(50L, "XAF"))
                .priority(10).build();

        LoyaltyRule loyalty = LoyaltyRule.builder()
                .id(UUID.randomUUID())
                .minimumTransactionCount(10).periodDays(90)
                .discountPercentage(new BigDecimal("5")).build();

        when(policyRepository.findByIdAndTenantId(POLICY_ID, TENANT_ID))
                .thenReturn(Mono.just(activePolicy(List.of(rule), null, List.of(loyalty))));

        StepVerifier.create(pricingEngineService.evaluatePolicy(POLICY_ID, ydeCtx()))
                .expectNextMatches(eval -> {
                    BigDecimal price = eval.getSellingPrice().getAmount();
                    return price.compareTo(new BigDecimal("1425")) < 0
                            && eval.getDiscountApplied().getAmount().compareTo(BigDecimal.ZERO) > 0;
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("no matching rule → NoPricingRuleMatchException")
    void testNoRuleMatch() {
        PricingRule rule = PricingRule.builder()
                .id(UUID.randomUUID()).name("Heavy")
                .conditionExpression("weight > 50")
                .basePrice(Money.of(5000L, "XAF")).priority(10).build();

        when(policyRepository.findByIdAndTenantId(POLICY_ID, TENANT_ID))
                .thenReturn(Mono.just(activePolicy(List.of(rule), null, null)));

        StepVerifier.create(pricingEngineService.evaluatePolicy(POLICY_ID, ydeCtx()))
                .expectError(NoPricingRuleMatchException.class)
                .verify();
    }

    @Test
    @DisplayName("inactive policy → PolicyNotActiveException")
    void testInactivePolicy() {
        BillingPolicy inactive = activePolicy(
                List.of(PricingRule.builder().id(UUID.randomUUID()).name("R")
                        .conditionExpression("weight <= 100")
                        .basePrice(Money.of(1000L, "XAF")).priority(10).build()),
                null, null)
                .deactivate();

        when(policyRepository.findByIdAndTenantId(POLICY_ID, TENANT_ID)).thenReturn(Mono.just(inactive));

        StepVerifier.create(pricingEngineService.evaluatePolicy(POLICY_ID, ydeCtx()))
                .expectError(PolicyNotActiveException.class)
                .verify();
    }

    @Test
    @DisplayName("minimum price floor is applied when base+km is below minimum")
    void testMinimumPrice() {
        PricingRule rule = PricingRule.builder()
                .id(UUID.randomUUID()).name("Standard")
                .conditionExpression("weight <= 100")
                .basePrice(Money.of(100L, "XAF"))
                .perKmRate(Money.of(10L, "XAF"))
                .minimumPrice(Money.of(2000L, "XAF"))
                .priority(10).build();

        when(policyRepository.findByIdAndTenantId(POLICY_ID, TENANT_ID))
                .thenReturn(Mono.just(activePolicy(List.of(rule), null, null)));

        StepVerifier.create(pricingEngineService.evaluatePolicy(POLICY_ID, ydeCtx()))
                .expectNextMatches(eval ->
                        eval.getSellingPrice().getAmount().compareTo(new BigDecimal("2000")) == 0)
                .verifyComplete();
    }

    @Test
    @DisplayName("rules evaluated in ascending priority order — lowest priority number wins")
    void testRulePriorityOrder() {
        PricingRule low = PricingRule.builder()
                .id(UUID.randomUUID()).name("Low")
                .conditionExpression("weight <= 100")
                .basePrice(Money.of(3000L, "XAF")).priority(100).build();

        PricingRule high = PricingRule.builder()
                .id(UUID.randomUUID()).name("High")
                .conditionExpression("weight <= 100")
                .basePrice(Money.of(1000L, "XAF")).priority(10).build();

        when(policyRepository.findByIdAndTenantId(POLICY_ID, TENANT_ID))
                .thenReturn(Mono.just(activePolicy(List.of(low, high), null, null)));

        StepVerifier.create(pricingEngineService.evaluatePolicy(POLICY_ID, ydeCtx()))
                .expectNextMatches(eval ->
                        eval.getSellingPrice().getAmount().compareTo(new BigDecimal("1000")) == 0)
                .verifyComplete();
    }

    @Test
    @DisplayName("IDOR (Audit n°7 · #5): evaluatePolicy must not evaluate another tenant's policy")
    void testEvaluatePolicyRejectsCrossTenantAccess() {
        UUID otherTenantId = UUID.randomUUID();
        PricingContext otherTenantCtx = ydeCtx().toBuilder().tenantId(otherTenantId).build();

        // The policy exists under TENANT_ID, but the caller's context carries otherTenantId.
        // findByIdAndTenantId(POLICY_ID, otherTenantId) must come back empty — the caller must
        // not be able to evaluate (and thereby infer the pricing rules of) a policy belonging
        // to a different tenant.
        when(policyRepository.findByIdAndTenantId(POLICY_ID, otherTenantId)).thenReturn(Mono.empty());

        StepVerifier.create(pricingEngineService.evaluatePolicy(POLICY_ID, otherTenantCtx))
                .expectError(com.yowyob.tiibntick.core.billing.pricing.domain.exception
                        .BillingPolicyNotFoundException.class)
                .verify();
    }
}
