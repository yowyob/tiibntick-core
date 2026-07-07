package com.yowyob.tiibntick.core.billing.dsl.infrastructure.dsl;

import com.yowyob.tiibntick.core.billing.dsl.domain.model.*;
import com.yowyob.tiibntick.core.billing.dsl.infrastructure.dsl.ast.AstNode;
import com.yowyob.tiibntick.core.billing.dsl.infrastructure.dsl.evaluator.DslEvaluator;
import com.yowyob.tiibntick.core.billing.dsl.infrastructure.dsl.lexer.DslLexer;
import com.yowyob.tiibntick.core.billing.dsl.infrastructure.dsl.parser.DslParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the DSL evaluator, verifying condition correctness
 * across various pricing contexts.
 *
 * @author MANFOUO Braun
 */
@DisplayName("DslEvaluator — condition evaluation tests")
class DslEvaluatorTest {

    private DslLexer lexer;
    private DslParser parser;
    private DslEvaluator evaluator;

    @BeforeEach
    void setUp() {
        lexer = new DslLexer();
        parser = new DslParser();
        evaluator = new DslEvaluator();
    }

    private boolean eval(String condition, PricingContext ctx) {
        AstNode ast = parser.parseCondition(lexer.tokenize(condition));
        return evaluator.evaluate(ast, ctx);
    }

    private PricingContext standardCtx() {
        return PricingContext.builder()
                .weightKg(3.2)
                .distanceKm(8.5)
                .packageTypes(List.of(PackageType.FRAGILE))
                .priority(DeliveryPriority.HIGH)
                .clientTxCount(15)
                .timeOfDay(LocalTime.of(10, 0))
                .weatherCondition(WeatherCondition.RAIN_LIGHT)
                .roadType(RoadType.PAVED)
                .tenantId(UUID.randomUUID())
                .agencyId(UUID.randomUUID())
                .build();
    }

    @Test
    @DisplayName("weight <= 5 should be TRUE for weight=3.2")
    void testWeightLTE() {
        assertThat(eval("weight <= 5", standardCtx())).isTrue();
    }

    @Test
    @DisplayName("weight <= 3 should be FALSE for weight=3.2")
    void testWeightLTEFalse() {
        assertThat(eval("weight <= 3", standardCtx())).isFalse();
    }

    @Test
    @DisplayName("distance <= 10 AND weight <= 5 should be TRUE")
    void testAndExpression() {
        assertThat(eval("distance <= 10 AND weight <= 5", standardCtx())).isTrue();
    }

    @Test
    @DisplayName("distance > 10 OR weight <= 5 should be TRUE (short circuit OR)")
    void testOrExpression() {
        assertThat(eval("distance > 10 OR weight <= 5", standardCtx())).isTrue();
    }

    @Test
    @DisplayName("packageType == FRAGILE should be TRUE")
    void testPackageTypeEquals() {
        assertThat(eval("packageType == 'FRAGILE'", standardCtx())).isTrue();
    }

    @Test
    @DisplayName("packageType == STANDARD should be FALSE")
    void testPackageTypeEqualsFalse() {
        assertThat(eval("packageType == 'STANDARD'", standardCtx())).isFalse();
    }

    @Test
    @DisplayName("priority == HIGH should be TRUE")
    void testPriorityEquals() {
        assertThat(eval("priority == 'HIGH'", standardCtx())).isTrue();
    }

    @Test
    @DisplayName("packageType IN [FRAGILE, ELECTRONICS] should be TRUE")
    void testInList() {
        assertThat(eval("packageType IN [FRAGILE, ELECTRONICS]", standardCtx())).isTrue();
    }

    @Test
    @DisplayName("packageType IN [LIQUID, STANDARD] should be FALSE")
    void testInListFalse() {
        assertThat(eval("packageType IN [LIQUID, STANDARD]", standardCtx())).isFalse();
    }

    @Test
    @DisplayName("distance BETWEEN 5 AND 15 should be TRUE for distance=8.5")
    void testBetweenTrue() {
        assertThat(eval("distance BETWEEN 5 AND 15", standardCtx())).isTrue();
    }

    @Test
    @DisplayName("distance BETWEEN 10 AND 20 should be FALSE for distance=8.5")
    void testBetweenFalse() {
        assertThat(eval("distance BETWEEN 10 AND 20", standardCtx())).isFalse();
    }

    @Test
    @DisplayName("clientTxCount >= 10 should be TRUE for txCount=15 (loyalty rule)")
    void testLoyaltyCondition() {
        assertThat(eval("clientTxCount >= 10", standardCtx())).isTrue();
    }

    @Test
    @DisplayName("isRaining == true should be TRUE for RAIN_LIGHT weather")
    void testIsRaining() {
        assertThat(eval("isRaining == true", standardCtx())).isTrue();
    }

    @Test
    @DisplayName("isRaining == false should be FALSE for RAIN_LIGHT weather")
    void testIsRainingFalse() {
        assertThat(eval("isRaining == false", standardCtx())).isFalse();
    }

    @Test
    @DisplayName("NOT isRoadDegraded == true should be TRUE for PAVED road")
    void testNotRoadDegraded() {
        assertThat(eval("NOT isRoadDegraded == true", standardCtx())).isTrue();
    }

    @Test
    @DisplayName("complex rule: weight <= 5 AND packageType == FRAGILE AND priority IN [HIGH, URGENT]")
    void testComplexRule() {
        String condition = "weight <= 5 AND packageType == 'FRAGILE' AND priority IN [HIGH, URGENT]";
        assertThat(eval(condition, standardCtx())).isTrue();
    }

    @Test
    @DisplayName("complex rule should be FALSE when one sub-condition fails")
    void testComplexRuleFalse() {
        String condition = "weight <= 2 AND packageType == 'FRAGILE'";
        assertThat(eval(condition, standardCtx())).isFalse();
    }
}
