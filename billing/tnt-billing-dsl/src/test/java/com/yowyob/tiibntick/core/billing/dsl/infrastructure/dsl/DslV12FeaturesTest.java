package com.yowyob.tiibntick.core.billing.dsl.infrastructure.dsl;

import com.yowyob.tiibntick.core.billing.dsl.application.service.DslCompilerService;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.*;
import com.yowyob.tiibntick.core.billing.dsl.infrastructure.dsl.ast.AstNode;
import com.yowyob.tiibntick.core.billing.dsl.infrastructure.dsl.evaluator.DslEvaluator;
import com.yowyob.tiibntick.core.billing.dsl.infrastructure.dsl.lexer.DslLexer;
import com.yowyob.tiibntick.core.billing.dsl.infrastructure.dsl.parser.DslParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for  DSL features: CONTAINS, DAY_IS, TIME_IS_BETWEEN,
 * and new PricingContext variables.
 *
 * @author MANFOUO Braun
 */
class DslV12FeaturesTest {

    private DslCompilerService compiler;
    private DslEvaluator evaluator;

    @BeforeEach
    void setUp() {
        DslLexer lexer = new DslLexer();
        DslParser parser = new DslParser();
        compiler = new DslCompilerService(lexer, parser);
        evaluator = new DslEvaluator();
    }

    private PricingContext baseContext() {
        return PricingContext.builder()
                .weightKg(3.0)
                .distanceKm(8.0)
                .tenantId(UUID.randomUUID())
                .clientTxCount(5)
                .timeOfDay(LocalTime.of(14, 0))
                .dayOfWeek(DayOfWeek.WEDNESDAY)
                .build();
    }

    // ── CONTAINS operator ─────────────────────────────────────────────────────

    @Test
    @DisplayName("CONTAINS — activeEquipmentTypes CONTAINS REFRIGERATED_BOX → true when present")
    void contains_refrigeratedBox_present() {
        AstNode ast = compiler.compileCondition(
                "activeEquipmentTypes CONTAINS REFRIGERATED_BOX");
        PricingContext ctx = baseContext().toBuilder()
                .activeEquipmentTypeCodes(Set.of("REFRIGERATED_BOX", "GPS_TRACKER"))
                .build();
        assertThat(evaluator.evaluate(ast, ctx)).isTrue();
    }

    @Test
    @DisplayName("CONTAINS — activeEquipmentTypes CONTAINS REFRIGERATED_BOX → false when absent")
    void contains_refrigeratedBox_absent() {
        AstNode ast = compiler.compileCondition(
                "activeEquipmentTypes CONTAINS REFRIGERATED_BOX");
        PricingContext ctx = baseContext().toBuilder()
                .activeEquipmentTypeCodes(Set.of("THERMAL_BAG"))
                .build();
        assertThat(evaluator.evaluate(ast, ctx)).isFalse();
    }

    @Test
    @DisplayName("CONTAINS — empty equipment set → false")
    void contains_emptySet_returnsFalse() {
        AstNode ast = compiler.compileCondition(
                "activeEquipmentTypes CONTAINS REFRIGERATED_BOX");
        PricingContext ctx = baseContext().toBuilder()
                .activeEquipmentTypeCodes(Set.of())
                .build();
        assertThat(evaluator.evaluate(ast, ctx)).isFalse();
    }

    @Test
    @DisplayName("CONTAINS with AND — combined equipment and zone rule")
    void contains_combinedWithAnd() {
        AstNode ast = compiler.compileCondition(
                "activeEquipmentTypes CONTAINS REFRIGERATED_BOX AND requiresRefrigeration == true");
        PricingContext ctx = baseContext().toBuilder()
                .activeEquipmentTypeCodes(Set.of("REFRIGERATED_BOX"))
                .requiresRefrigeration(true)
                .build();
        assertThat(evaluator.evaluate(ast, ctx)).isTrue();
    }

    // ── DAY_IS operator ───────────────────────────────────────────────────────

    @Test
    @DisplayName("DAY_IS WEEKEND → true on Saturday")
    void dayIs_weekend_saturday() {
        AstNode ast = compiler.compileCondition("dayOfWeek DAY_IS WEEKEND");
        PricingContext ctx = baseContext().toBuilder()
                .dayOfWeek(DayOfWeek.SATURDAY).build();
        assertThat(evaluator.evaluate(ast, ctx)).isTrue();
    }

    @Test
    @DisplayName("DAY_IS WEEKEND → true on Sunday")
    void dayIs_weekend_sunday() {
        AstNode ast = compiler.compileCondition("dayOfWeek DAY_IS WEEKEND");
        PricingContext ctx = baseContext().toBuilder()
                .dayOfWeek(DayOfWeek.SUNDAY).build();
        assertThat(evaluator.evaluate(ast, ctx)).isTrue();
    }

    @Test
    @DisplayName("DAY_IS WEEKEND → false on Wednesday")
    void dayIs_weekend_wednesday() {
        AstNode ast = compiler.compileCondition("dayOfWeek DAY_IS WEEKEND");
        PricingContext ctx = baseContext().toBuilder()
                .dayOfWeek(DayOfWeek.WEDNESDAY).build();
        assertThat(evaluator.evaluate(ast, ctx)).isFalse();
    }

    @Test
    @DisplayName("DAY_IS WEEKDAY → true on Monday–Friday")
    void dayIs_weekday_friday() {
        AstNode ast = compiler.compileCondition("dayOfWeek DAY_IS WEEKDAY");
        PricingContext ctx = baseContext().toBuilder()
                .dayOfWeek(DayOfWeek.FRIDAY).build();
        assertThat(evaluator.evaluate(ast, ctx)).isTrue();
    }

    @Test
    @DisplayName("DAY_IS HOLIDAY → true when isPublicHoliday=true")
    void dayIs_holiday() {
        AstNode ast = compiler.compileCondition("dayOfWeek DAY_IS HOLIDAY");
        PricingContext ctx = baseContext().toBuilder()
                .isPublicHoliday(true).build();
        assertThat(evaluator.evaluate(ast, ctx)).isTrue();
    }

    @Test
    @DisplayName("DAY_IS SATURDAY → true only on Saturday")
    void dayIs_specificDay() {
        AstNode ast = compiler.compileCondition("dayOfWeek DAY_IS SATURDAY");
        PricingContext satCtx = baseContext().toBuilder()
                .dayOfWeek(DayOfWeek.SATURDAY).build();
        PricingContext friCtx = baseContext().toBuilder()
                .dayOfWeek(DayOfWeek.FRIDAY).build();
        assertThat(evaluator.evaluate(ast, satCtx)).isTrue();
        assertThat(evaluator.evaluate(ast, friCtx)).isFalse();
    }

    // ── TIME_IS_BETWEEN operator ──────────────────────────────────────────────

    @Test
    @DisplayName("TIME_IS_BETWEEN 08:00 AND 18:00 → true at 14:00 (business hours)")
    void timeIsBetween_businessHours_within() {
        AstNode ast = compiler.compileCondition(
                "timeOfDay TIME_IS_BETWEEN 08:00 AND 18:00");
        PricingContext ctx = baseContext().toBuilder()
                .timeOfDay(LocalTime.of(14, 0)).build();
        assertThat(evaluator.evaluate(ast, ctx)).isTrue();
    }

    @Test
    @DisplayName("TIME_IS_BETWEEN 08:00 AND 18:00 → false at 20:00")
    void timeIsBetween_businessHours_outside() {
        AstNode ast = compiler.compileCondition(
                "timeOfDay TIME_IS_BETWEEN 08:00 AND 18:00");
        PricingContext ctx = baseContext().toBuilder()
                .timeOfDay(LocalTime.of(20, 0)).build();
        assertThat(evaluator.evaluate(ast, ctx)).isFalse();
    }

    @Test
    @DisplayName("TIME_IS_BETWEEN 22:00 AND 06:00 → true at 23:30 (overnight range)")
    void timeIsBetween_overnight_within() {
        AstNode ast = compiler.compileCondition(
                "timeOfDay TIME_IS_BETWEEN 22:00 AND 06:00");
        PricingContext ctx = baseContext().toBuilder()
                .timeOfDay(LocalTime.of(23, 30)).build();
        assertThat(evaluator.evaluate(ast, ctx)).isTrue();
    }

    @Test
    @DisplayName("TIME_IS_BETWEEN 22:00 AND 06:00 → true at 02:00 (crosses midnight)")
    void timeIsBetween_overnight_crossesMidnight() {
        AstNode ast = compiler.compileCondition(
                "timeOfDay TIME_IS_BETWEEN 22:00 AND 06:00");
        PricingContext ctx = baseContext().toBuilder()
                .timeOfDay(LocalTime.of(2, 0)).build();
        assertThat(evaluator.evaluate(ast, ctx)).isTrue();
    }

    @Test
    @DisplayName("TIME_IS_BETWEEN 22:00 AND 06:00 → false at 12:00")
    void timeIsBetween_overnight_daytime() {
        AstNode ast = compiler.compileCondition(
                "timeOfDay TIME_IS_BETWEEN 22:00 AND 06:00");
        PricingContext ctx = baseContext().toBuilder()
                .timeOfDay(LocalTime.of(12, 0)).build();
        assertThat(evaluator.evaluate(ast, ctx)).isFalse();
    }

    // ── New variables ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("requiresRefrigeration == true → evaluates correctly")
    void requiresRefrigeration_variable() {
        AstNode ast = compiler.compileCondition("requiresRefrigeration == true");
        PricingContext ctx = baseContext().toBuilder()
                .requiresRefrigeration(true).build();
        assertThat(evaluator.evaluate(ast, ctx)).isTrue();
    }

    @Test
    @DisplayName("zoneType == RURAL → evaluates correctly")
    void zoneType_rural() {
        AstNode ast = compiler.compileCondition("zoneType == RURAL");
        PricingContext ctx = baseContext().toBuilder()
                .deliveryZoneType("RURAL").build();
        assertThat(evaluator.evaluate(ast, ctx)).isTrue();
    }

    @Test
    @DisplayName("packageCount >= 5 → evaluates correctly")
    void packageCount_gte() {
        AstNode ast = compiler.compileCondition("packageCount >= 5");
        PricingContext ctx = baseContext().toBuilder()
                .packageCount(7).build();
        assertThat(evaluator.evaluate(ast, ctx)).isTrue();
    }

    @Test
    @DisplayName("vehicleType == MOTO → matches vehicle type")
    void vehicleType_match() {
        AstNode ast = compiler.compileCondition("vehicleType == MOTO");
        PricingContext ctx = baseContext().toBuilder()
                .selectedVehicleType("MOTO").build();
        assertThat(evaluator.evaluate(ast, ctx)).isTrue();
    }

    @Test
    @DisplayName("storageHours >= 24 → hub storage surcharge trigger")
    void storageHours_threshold() {
        AstNode ast = compiler.compileCondition("storageHours >= 24");
        PricingContext ctx = baseContext().toBuilder()
                .storageHours(48).build();
        assertThat(evaluator.evaluate(ast, ctx)).isTrue();
    }

    @Test
    @DisplayName("networkHops >= 2 → link network surcharge trigger")
    void networkHops_threshold() {
        AstNode ast = compiler.compileCondition("networkHops >= 2");
        PricingContext ctx = baseContext().toBuilder()
                .networkHopCount(3).build();
        assertThat(evaluator.evaluate(ast, ctx)).isTrue();
    }
}
