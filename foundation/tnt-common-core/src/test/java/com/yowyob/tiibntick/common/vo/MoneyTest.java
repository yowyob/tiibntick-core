package com.yowyob.tiibntick.common.vo;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for the Money value object.
 * Author: MANFOUO Braun
 */
class MoneyTest {

    @Test
    void should_create_xaf_money_with_zero_decimals() {
        Money xaf = Money.of(1500L, "XAF");
        assertThat(xaf.getAmount()).isEqualByComparingTo("1500");
        assertThat(xaf.getCurrencyCode()).isEqualTo("XAF");
    }

    @Test
    void should_add_same_currency() {
        Money a = Money.of(1000L, "XAF");
        Money b = Money.of(500L, "XAF");
        assertThat(a.add(b).getAmount()).isEqualByComparingTo("1500");
    }

    @Test
    void should_reject_cross_currency_operation() {
        Money xaf = Money.of(1000L, "XAF");
        Money usd = Money.of(BigDecimal.ONE, "USD");
        assertThatThrownBy(() -> xaf.add(usd))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Currency mismatch");
    }

    @Test
    void should_multiply_by_factor() {
        Money base = Money.of(1000L, "XAF");
        Money result = base.multiply(1.5);
        assertThat(result.getAmount()).isEqualByComparingTo("1500");
    }

    @Test
    void should_detect_zero_and_negative() {
        assertThat(Money.ZERO_XAF.isZero()).isTrue();
        assertThat(Money.of(-100L, "XAF").isNegative()).isTrue();
        assertThat(Money.of(100L, "XAF").isPositive()).isTrue();
    }
}
