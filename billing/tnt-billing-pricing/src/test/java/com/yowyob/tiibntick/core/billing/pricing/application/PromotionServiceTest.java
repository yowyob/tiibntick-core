package com.yowyob.tiibntick.core.billing.pricing.application;

import com.yowyob.tiibntick.core.billing.pricing.application.service.PromotionService;
import com.yowyob.tiibntick.core.billing.pricing.domain.model.BillingPolicy;
import com.yowyob.tiibntick.core.billing.pricing.domain.model.PricingRule;
import com.yowyob.tiibntick.core.billing.pricing.domain.model.Promotion;
import com.yowyob.tiibntick.core.billing.pricing.domain.model.enums.DiscountType;
import com.yowyob.tiibntick.core.billing.pricing.domain.model.enums.PolicyStatus;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PromotionService — code validation and discount application")
class PromotionServiceTest {

    @Mock
    private IBillingPolicyRepository policyRepository;

    private PromotionService promotionService;

    private final UUID POLICY_ID = UUID.randomUUID();
    private final UUID TENANT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        DslLexer lexer = new DslLexer();
        DslParser parser = new DslParser();
        DslCompilerService compiler = new DslCompilerService(lexer, parser);
        DslEvaluator evaluator = new DslEvaluator();
        promotionService = new PromotionService(policyRepository, compiler, evaluator);
    }

    private Promotion activePromo(String code, DiscountType type, BigDecimal value) {
        return Promotion.builder()
                .id(UUID.randomUUID())
                .name("Test Promo")
                .code(code)
                .discountType(type)
                .discountValue(value)
                .validFrom(LocalDateTime.now().minusDays(1))
                .validTo(LocalDateTime.now().plusDays(30))
                .currentUsages(0)
                .maxUsagesTotal(100)
                .build();
    }

    private BillingPolicy policyWithPromo(Promotion promo) {
        return BillingPolicy.builder()
                .id(POLICY_ID).tenantId(TENANT_ID).name("Test")
                .pricingRules(List.of(PricingRule.builder().id(UUID.randomUUID())
                        .name("R").conditionExpression("weight <= 100")
                        .basePrice(Money.of(1000L, "XAF")).priority(10).build()))
                .surchargeRules(List.of())
                .promotions(promo != null ? List.of(promo) : List.of())
                .loyaltyRules(List.of())
                .commissionRules(List.of())
                .isDefault(false)
                .status(PolicyStatus.ACTIVE)
                .validFrom(LocalDate.now())
                .build();
    }

    private PricingContext simpleCtx() {
        return PricingContext.builder()
                .weightKg(3.0).distanceKm(5.0)
                .tenantId(TENANT_ID).build();
    }

    @Test
    @DisplayName("valid PERCENTAGE promo code applies correct discount")
    void testPercentagePromo() {
        Promotion promo = activePromo("SAVE10", DiscountType.PERCENTAGE, new BigDecimal("10"));
        when(policyRepository.findByIdAndTenantId(POLICY_ID, TENANT_ID)).thenReturn(Mono.just(policyWithPromo(promo)));

        Money price = Money.of(2000L, "XAF");

        StepVerifier.create(promotionService.applyPromoCode(POLICY_ID, "SAVE10", price, simpleCtx()))
                .expectNextMatches(opt ->
                        opt.isPresent() && opt.get().getAmount().compareTo(new BigDecimal("200")) == 0)
                .verifyComplete();
    }

    @Test
    @DisplayName("valid FIXED promo code applies correct discount")
    void testFixedPromo() {
        Promotion promo = activePromo("FLAT500", DiscountType.FIXED, new BigDecimal("500"));
        when(policyRepository.findByIdAndTenantId(POLICY_ID, TENANT_ID)).thenReturn(Mono.just(policyWithPromo(promo)));

        Money price = Money.of(2000L, "XAF");

        StepVerifier.create(promotionService.applyPromoCode(POLICY_ID, "FLAT500", price, simpleCtx()))
                .expectNextMatches(opt ->
                        opt.isPresent() && opt.get().getAmount().compareTo(new BigDecimal("500")) == 0)
                .verifyComplete();
    }

    @Test
    @DisplayName("unknown promo code returns empty Optional")
    void testUnknownPromoCode() {
        when(policyRepository.findByIdAndTenantId(POLICY_ID, TENANT_ID))
                .thenReturn(Mono.just(policyWithPromo(activePromo("VALID", DiscountType.FIXED, BigDecimal.TEN))));

        StepVerifier.create(promotionService.applyPromoCode(POLICY_ID, "WRONG", Money.of(2000L, "XAF"), simpleCtx()))
                .expectNextMatches(Optional::isEmpty)
                .verifyComplete();
    }

    @Test
    @DisplayName("expired promo code returns empty Optional")
    void testExpiredPromo() {
        Promotion expired = Promotion.builder()
                .id(UUID.randomUUID()).name("Expired").code("OLD")
                .discountType(DiscountType.PERCENTAGE).discountValue(new BigDecimal("10"))
                .validFrom(LocalDateTime.now().minusDays(30))
                .validTo(LocalDateTime.now().minusDays(1))
                .currentUsages(0)
                .build();

        when(policyRepository.findByIdAndTenantId(POLICY_ID, TENANT_ID)).thenReturn(Mono.just(policyWithPromo(expired)));

        StepVerifier.create(promotionService.applyPromoCode(POLICY_ID, "OLD", Money.of(2000L, "XAF"), simpleCtx()))
                .expectNextMatches(Optional::isEmpty)
                .verifyComplete();
    }

    @Test
    @DisplayName("validatePromoCode returns true for valid active code")
    void testValidateActiveCode() {
        Promotion promo = activePromo("ACTIVE", DiscountType.FIXED, BigDecimal.TEN);
        when(policyRepository.findByIdAndTenantId(POLICY_ID, TENANT_ID)).thenReturn(Mono.just(policyWithPromo(promo)));

        StepVerifier.create(promotionService.validatePromoCode(POLICY_ID, "ACTIVE", simpleCtx()))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    @DisplayName("IDOR (Audit n°7 · #5): applyPromoCode must not probe another tenant's promotions")
    void testApplyPromoCodeRejectsCrossTenantAccess() {
        UUID otherTenantId = UUID.randomUUID();
        PricingContext otherTenantCtx = simpleCtx().toBuilder().tenantId(otherTenantId).build();
        when(policyRepository.findByIdAndTenantId(POLICY_ID, otherTenantId)).thenReturn(Mono.empty());

        StepVerifier.create(promotionService.applyPromoCode(
                        POLICY_ID, "SAVE10", Money.of(2000L, "XAF"), otherTenantCtx))
                .expectError(com.yowyob.tiibntick.core.billing.pricing.domain.exception
                        .BillingPolicyNotFoundException.class)
                .verify();
    }
}
