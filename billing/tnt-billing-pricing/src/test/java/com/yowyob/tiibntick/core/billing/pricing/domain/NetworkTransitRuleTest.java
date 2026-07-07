package com.yowyob.tiibntick.core.billing.pricing.domain;

import com.yowyob.tiibntick.core.billing.pricing.domain.model.NetworkTransitRule;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link NetworkTransitRule} domain model.
 *
 * @author MANFOUO Braun
 */
class NetworkTransitRuleTest {

    private static NetworkTransitRule basicRule() {
        return NetworkTransitRule.builder()
                .id(UUID.randomUUID())
                .maxHops(0)
                .perHopFee(BigDecimal.valueOf(200))
                .nodeHandlingFee(BigDecimal.valueOf(50))
                .isInterCity(false)
                .interCityTransitFee(BigDecimal.valueOf(1000))
                .build();
    }

    @Test
    @DisplayName("2 hops same-city: 2 × (200 + 50) = 500 XAF")
    void twoHops_sameCity() {
        NetworkTransitRule rule = basicRule();
        Money fee = rule.computeFee(2, false, "XAF");
        assertThat(fee.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(500));
    }

    @Test
    @DisplayName("2 hops inter-city: 2 × 250 + 1000 = 1500 XAF")
    void twoHops_interCity() {
        NetworkTransitRule rule = basicRule();
        Money fee = rule.computeFee(2, true, "XAF");
        assertThat(fee.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(1500));
    }

    @Test
    @DisplayName("0 hops returns 0 XAF")
    void zeroHops_returnsZero() {
        NetworkTransitRule rule = basicRule();
        Money fee = rule.computeFee(0, false, "XAF");
        assertThat(fee.getAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("appliesTo — maxHops 0 means unlimited")
    void appliesTo_unlimitedHops() {
        NetworkTransitRule rule = basicRule();
        assertThat(rule.appliesTo(10, false)).isTrue();
    }
}
