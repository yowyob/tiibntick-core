package com.yowyob.tiibntick.core.billing.pricing.domain;

import com.yowyob.tiibntick.core.billing.pricing.domain.model.SpecialSurchargeRule;
import com.yowyob.tiibntick.core.billing.pricing.domain.model.enums.SurchargeStackMode;
import com.yowyob.tiibntick.core.billing.pricing.domain.model.enums.SurchargeType;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link SpecialSurchargeRule} domain model.
 *
 * @author MANFOUO Braun
 */
class SpecialSurchargeRuleTest {

    private static SpecialSurchargeRule fixed(BigDecimal amount) {
        return SpecialSurchargeRule.builder()
                .id(UUID.randomUUID())
                .surchargeCode("TEST_FIXED")
                .triggerCondition("requiresRefrigeration == true")
                .surchargeType(SurchargeType.FIXED)
                .value(amount)
                .stackMode(SurchargeStackMode.CUMULATIVE)
                .isActive(true)
                .build();
    }

    private static SpecialSurchargeRule percentage(BigDecimal pct) {
        return SpecialSurchargeRule.builder()
                .id(UUID.randomUUID())
                .surchargeCode("TEST_PCT")
                .triggerCondition("dayOfWeek DAY_IS WEEKEND")
                .surchargeType(SurchargeType.PERCENTAGE)
                .value(pct)
                .stackMode(SurchargeStackMode.CUMULATIVE)
                .isActive(true)
                .build();
    }

    @Test
    @DisplayName("FIXED surcharge: computeSurcharge returns fixed amount")
    void fixed_computeSurcharge() {
        SpecialSurchargeRule rule = fixed(BigDecimal.valueOf(500));
        Money base = Money.of(2000, "XAF");
        Money surcharge = rule.computeSurcharge(base);
        assertThat(surcharge.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(500));
    }

    @Test
    @DisplayName("PERCENTAGE surcharge: computeSurcharge returns percentage of base")
    void percentage_computeSurcharge() {
        SpecialSurchargeRule rule = percentage(BigDecimal.valueOf(15));
        Money base = Money.of(2000, "XAF");
        Money surcharge = rule.computeSurcharge(base);
        assertThat(surcharge.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(300));
    }

    @Test
    @DisplayName("apply() returns base + surcharge")
    void apply_addsToBase() {
        SpecialSurchargeRule rule = fixed(BigDecimal.valueOf(500));
        Money base = Money.of(2000, "XAF");
        Money result = rule.apply(base);
        assertThat(result.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(2500));
    }

    @Test
    @DisplayName("Inactive rule has isActive=false")
    void inactiveRule() {
        SpecialSurchargeRule inactive = SpecialSurchargeRule.builder()
                .id(UUID.randomUUID()).surchargeCode("INACTIVE")
                .triggerCondition("true").surchargeType(SurchargeType.FIXED)
                .value(BigDecimal.valueOf(100)).stackMode(SurchargeStackMode.CUMULATIVE)
                .isActive(false).build();
        assertThat(inactive.isActive()).isFalse();
    }
}
