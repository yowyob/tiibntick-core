package com.yowyob.tiibntick.core.billing.dsl.application;

import com.yowyob.tiibntick.core.billing.dsl.application.service.DslCompilerService;
import com.yowyob.tiibntick.core.billing.dsl.application.service.PricingEngine;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.*;
import com.yowyob.tiibntick.core.billing.dsl.infrastructure.dsl.evaluator.DslEvaluator;
import com.yowyob.tiibntick.core.billing.dsl.infrastructure.dsl.executor.ActionExecutor;
import com.yowyob.tiibntick.core.billing.dsl.infrastructure.dsl.lexer.DslLexer;
import com.yowyob.tiibntick.core.billing.dsl.infrastructure.dsl.parser.DslParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration-style tests for the full DSL evaluation pipeline:
 * DslCompilerService → PricingEngine → EvaluationResult.
 * <p>
 * Scenario: Yaoundé standard zone — delivery example from the conception:
 * weight=3.2 kg, distance=8.5 km, FRAGILE, HIGH priority, 15 past transactions.
 * Expected:
 *   base    = 1000 XAF
 *   perKm   = 8.5 × 50 = 425 XAF  → subtotal 1425
 *   fragile = +500 XAF             → subtotal 1925
 *   HIGH    = +15% = +288 XAF      → subtotal 2213
 *   loyalty = -5% = -110 XAF       → final   2103 XAF
 * </p>
 *
 * @author MANFOUO Braun
 */
@DisplayName("PricingEngine — full DSL evaluation pipeline")
class PricingEngineTest {

    private PricingEngine engine;
    private DslCompilerService compiler;

    private static final UUID POLICY_ID = UUID.randomUUID();
    private static final UUID TENANT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        DslLexer lexer = new DslLexer();
        DslParser parser = new DslParser();
        DslEvaluator evaluator = new DslEvaluator();
        ActionExecutor executor = new ActionExecutor();
        compiler = new DslCompilerService(lexer, parser);
        engine = new PricingEngine(compiler, evaluator, executor);
    }

    private DslRule buildRule(String name, String condition, String action, int priority) {
        DslRule draft = DslRule.builder()
                .id(UUID.randomUUID())
                .name(name)
                .conditionExpression(condition)
                .actionExpression(action)
                .priority(priority)
                .active(true)
                .tenantId(TENANT_ID)
                .policyId(POLICY_ID)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        return compiler.compile(draft);
    }

    private PricingContext ydeStandardCtx() {
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
                .agencyId(UUID.randomUUID())
                .missionId("MISS-001")
                .build();
    }

    @Test
    @DisplayName("standard zone rule: weight<=5 AND distance<=10 → base=1000 XAF + 50/km")
    void testStandardZoneRule() {
        DslRule rule = buildRule(
                "Standard YDE",
                "weight <= 5 AND distance <= 10",
                "SET_BASE 1000 XAF SET_PER_KM 50 XAF",
                10);

        EvaluationResult result = engine.evaluate(List.of(rule), ydeStandardCtx(), "XAF");

        assertThat(result.hasMatch()).isTrue();
        // base 1000 + km (8.5 * 50 = 425) = 1425
        assertThat(result.getFinalPrice().getAmount())
                .isEqualByComparingTo(new BigDecimal("1425"));
    }

    @Test
    @DisplayName("fragile surcharge: +500 XAF when packageType == FRAGILE")
    void testFragileSurcharge() {
        DslRule baseRule = buildRule(
                "Standard YDE",
                "weight <= 5 AND distance <= 10",
                "SET_BASE 1000 XAF SET_PER_KM 50 XAF",
                10);

        DslRule fragileRule = buildRule(
                "Fragile Surcharge",
                "packageType == 'FRAGILE'",
                "ADD_FIXED 500 XAF",
                20);

        EvaluationResult result = engine.evaluate(
                List.of(baseRule, fragileRule), ydeStandardCtx(), "XAF");

        // base 1425 + 500 fragile = 1925
        assertThat(result.getFinalPrice().getAmount())
                .isEqualByComparingTo(new BigDecimal("1925"));
        assertThat(result.getSurcharges()).isNotEmpty();
    }

    @Test
    @DisplayName("HIGH priority surcharge: +15% on current price")
    void testHighPrioritySurcharge() {
        DslRule baseRule = buildRule(
                "Standard YDE",
                "weight <= 5 AND distance <= 10",
                "SET_BASE 1000 XAF SET_PER_KM 50 XAF",
                10);
        DslRule fragileRule = buildRule(
                "Fragile Surcharge",
                "packageType == 'FRAGILE'",
                "ADD_FIXED 500 XAF",
                20);
        DslRule highRule = buildRule(
                "High Priority Surcharge",
                "priority == 'HIGH'",
                "ADD_PCT 15",
                30);

        EvaluationResult result = engine.evaluate(
                List.of(baseRule, fragileRule, highRule), ydeStandardCtx(), "XAF");

        // 1925 + 15% = 1925 + 288.75 = 2213.75 ~ 2214
        assertThat(result.getFinalPrice().getAmount())
                .isEqualByComparingTo(new BigDecimal("2214"));
    }

    @Test
    @DisplayName("loyalty discount: -5% when clientTxCount >= 10")
    void testLoyaltyDiscount() {
        DslRule baseRule = buildRule(
                "Standard YDE",
                "weight <= 5 AND distance <= 10",
                "SET_BASE 1000 XAF SET_PER_KM 50 XAF",
                10);
        DslRule fragileRule = buildRule(
                "Fragile Surcharge",
                "packageType == 'FRAGILE'",
                "ADD_FIXED 500 XAF",
                20);
        DslRule highRule = buildRule(
                "High Priority Surcharge",
                "priority == 'HIGH'",
                "ADD_PCT 15",
                30);
        DslRule loyaltyRule = buildRule(
                "Loyalty -5%",
                "clientTxCount >= 10",
                "DISCOUNT_PCT 5",
                40);

        EvaluationResult result = engine.evaluate(
                List.of(baseRule, fragileRule, highRule, loyaltyRule),
                ydeStandardCtx(), "XAF");

        // 2213 - 5% = 2213 - 110 = 2103
        assertThat(result.getFinalPrice().getAmount())
                .isEqualByComparingTo(new BigDecimal("2103"));
        assertThat(result.getDiscounts()).isNotEmpty();
    }

    @Test
    @DisplayName("inactive rules should be skipped")
    void testInactiveRuleSkipped() {
        DslRule inactiveRule = DslRule.builder()
                .id(UUID.randomUUID())
                .name("Inactive Rule")
                .conditionExpression("weight <= 100")
                .actionExpression("ADD_FIXED 9999 XAF")
                .actions(List.of(DslAction.addFixed(new BigDecimal("9999"), "XAF")))
                .priority(1)
                .active(false)
                .tenantId(TENANT_ID)
                .policyId(POLICY_ID)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        EvaluationResult result = engine.evaluate(
                List.of(inactiveRule), ydeStandardCtx(), "XAF");

        assertThat(result.getFinalPrice().getAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getAppliedRules()).isEmpty();
    }

    @Test
    @DisplayName("rules evaluated in priority order (lowest priority number first)")
    void testPriorityOrder() {
        DslRule rule100 = buildRule("Low priority base", "weight <= 100",
                "SET_BASE 2000 XAF", 100);
        DslRule rule10 = buildRule("High priority base", "weight <= 100",
                "SET_BASE 1000 XAF", 10);

        EvaluationResult result = engine.evaluate(
                List.of(rule100, rule10), ydeStandardCtx(), "XAF");

        // Rule priority 10 fires first → SET_BASE 1000, then rule 100 → SET_BASE 2000
        assertThat(result.getFinalPrice().getAmount())
                .isEqualByComparingTo(new BigDecimal("2000"));
    }

    @Test
    @DisplayName("no rules match → returns zero price with empty applied list")
    void testNoMatch() {
        DslRule rule = buildRule(
                "No match",
                "weight > 1000",
                "SET_BASE 9999 XAF",
                10);

        EvaluationResult result = engine.evaluate(
                List.of(rule), ydeStandardCtx(), "XAF");

        assertThat(result.hasMatch()).isFalse();
        assertThat(result.getFinalPrice().getAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("BETWEEN operator: distance BETWEEN 5 AND 15 should match 8.5 km")
    void testBetweenOperator() {
        DslRule rule = buildRule(
                "Mid distance zone",
                "distance BETWEEN 5 AND 15",
                "SET_BASE 1200 XAF",
                10);

        EvaluationResult result = engine.evaluate(
                List.of(rule), ydeStandardCtx(), "XAF");

        assertThat(result.hasMatch()).isTrue();
        assertThat(result.getFinalPrice().getAmount())
                .isEqualByComparingTo(new BigDecimal("1200"));
    }

    @Test
    @DisplayName("IN list: packageType IN [FRAGILE, ELECTRONICS] should match FRAGILE")
    void testInListOperator() {
        DslRule rule = buildRule(
                "Fragile or Electronics",
                "packageType IN [FRAGILE, ELECTRONICS]",
                "ADD_FIXED 300 XAF",
                10);

        EvaluationResult result = engine.evaluate(
                List.of(rule), ydeStandardCtx(), "XAF");

        assertThat(result.hasMatch()).isTrue();
        assertThat(result.getFinalPrice().getAmount())
                .isEqualByComparingTo(new BigDecimal("300"));
    }
}
