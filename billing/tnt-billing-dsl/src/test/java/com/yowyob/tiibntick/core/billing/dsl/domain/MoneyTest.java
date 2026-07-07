package com.yowyob.tiibntick.core.billing.dsl.domain;

import com.yowyob.tiibntick.core.billing.dsl.domain.model.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link Money} value object.
 *
 * @author MANFOUO Braun
 */
@DisplayName("Money value object")
class MoneyTest {

    @Test
    @DisplayName("should add two XAF amounts correctly")
    void testAdd() {
        Money a = Money.of(1000L, "XAF");
        Money b = Money.of(500L, "XAF");
        assertThat(a.add(b).getAmount()).isEqualByComparingTo(new BigDecimal("1500"));
    }

    @Test
    @DisplayName("should subtract two XAF amounts correctly")
    void testSubtract() {
        Money a = Money.of(2000L, "XAF");
        Money b = Money.of(110L, "XAF");
        assertThat(a.subtract(b).getAmount()).isEqualByComparingTo(new BigDecimal("1890"));
    }

    @Test
    @DisplayName("should compute 15% of 1925 XAF = 289 XAF (rounded to 0 fractions for XAF)")
    void testPercentage() {
        Money base = Money.of(1925L, "XAF");
        Money pct = base.percentage(15);
        assertThat(pct.getAmount()).isEqualByComparingTo(new BigDecimal("289"));
    }

    @Test
    @DisplayName("should compute 5% of 2213 XAF = 111 XAF (rounded half up)")
    void testLoyaltyDiscount() {
        Money base = Money.of(2213L, "XAF");
        Money discount = base.percentage(5);
        // 2213 × 0.05 = 110.65 → rounded to 111
        assertThat(discount.getAmount()).isEqualByComparingTo(new BigDecimal("111"));
    }

    @Test
    @DisplayName("should multiply correctly")
    void testMultiply() {
        Money rate = Money.of(50L, "XAF");
        Money result = rate.multiply(8.5);
        assertThat(result.getAmount()).isEqualByComparingTo(new BigDecimal("425"));
    }

    @Test
    @DisplayName("should identify negative amounts")
    void testIsNegative() {
        Money negative = Money.of(-100L, "XAF");
        assertThat(negative.isNegative()).isTrue();
    }

    @Test
    @DisplayName("should identify zero amounts")
    void testIsZero() {
        assertThat(Money.zeroXAF().isZero()).isTrue();
    }

    @Test
    @DisplayName("should throw IllegalArgumentException when mixing currencies")
    void testCurrencyMismatch() {
        Money xaf = Money.of(1000L, "XAF");
        Money usd = Money.of(10L, "USD");
        assertThatThrownBy(() -> xaf.add(usd))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("currencies");
    }

    @Test
    @DisplayName("XAF has 0 fraction digits — amounts should be rounded to integers")
    void testXafScale() {
        Money m = Money.of(new BigDecimal("1000.7"), "XAF");
        assertThat(m.getAmount().scale()).isEqualTo(0);
        assertThat(m.getAmount()).isEqualByComparingTo(new BigDecimal("1001"));
    }
}
