package com.yowyob.tiibntick.core.billing.pricing.domain;

import com.yowyob.tiibntick.core.billing.pricing.domain.model.HubStorageRule;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.Money;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.PackageType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link HubStorageRule} domain model.
 *
 * @author MANFOUO Braun
 */
class HubStorageRuleTest {

    private static HubStorageRule ruleWithFreePeriod() {
        return HubStorageRule.builder()
                .id(UUID.randomUUID())
                .minHours(0).maxHours(0)
                .feePerInterval(BigDecimal.valueOf(500))
                .intervalHours(24)
                .isFreePeriod(true)
                .applicablePackageTypes(null)
                .build();
    }

    @Test
    @DisplayName("Free period: 0–24h returns 0 XAF")
    void freePeriod_noCharge() {
        HubStorageRule rule = ruleWithFreePeriod();
        Money fee = rule.computeFee(12, "XAF");
        assertThat(fee.getAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Free period: 48h = 1 billable interval = 500 XAF")
    void freePeriod_oneBillableInterval() {
        HubStorageRule rule = ruleWithFreePeriod();
        Money fee = rule.computeFee(48, "XAF");
        assertThat(fee.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(500));
    }

    @Test
    @DisplayName("Free period: 72h = 2 billable intervals = 1000 XAF")
    void freePeriod_twoBillableIntervals() {
        HubStorageRule rule = ruleWithFreePeriod();
        Money fee = rule.computeFee(72, "XAF");
        assertThat(fee.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(1000));
    }

    @Test
    @DisplayName("No free period: 24h = 1 interval = 500 XAF")
    void noFreePeriod_firstInterval() {
        HubStorageRule rule = HubStorageRule.builder()
                .id(UUID.randomUUID()).minHours(0).maxHours(0)
                .feePerInterval(BigDecimal.valueOf(500)).intervalHours(24)
                .isFreePeriod(false).build();
        Money fee = rule.computeFee(24, "XAF");
        assertThat(fee.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(500));
    }

    @Test
    @DisplayName("appliesTo — null package types means all types apply")
    void appliesTo_noRestrictions() {
        HubStorageRule rule = ruleWithFreePeriod();
        assertThat(rule.appliesTo(PackageType.FRAGILE)).isTrue();
        assertThat(rule.appliesTo(PackageType.STANDARD)).isTrue();
    }

    @Test
    @DisplayName("appliesTo — restricted list")
    void appliesTo_restrictedList() {
        HubStorageRule rule = HubStorageRule.builder()
                .id(UUID.randomUUID()).minHours(0).maxHours(0)
                .feePerInterval(BigDecimal.valueOf(500)).intervalHours(24)
                .isFreePeriod(false)
                .applicablePackageTypes(List.of(PackageType.PERISHABLE, PackageType.FRAGILE))
                .build();
        assertThat(rule.appliesTo(PackageType.PERISHABLE)).isTrue();
        assertThat(rule.appliesTo(PackageType.STANDARD)).isFalse();
    }
}
