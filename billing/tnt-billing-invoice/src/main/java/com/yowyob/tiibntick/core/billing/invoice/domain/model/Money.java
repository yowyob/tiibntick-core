package com.yowyob.tiibntick.core.billing.invoice.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Value Object: Money.
 *
 * <p>Represents a monetary amount with its currency.
 * Immutable and null-safe. All arithmetic preserves the same currency.
 * Supports XAF (Cameroon), NGN (Nigeria), KES (Kenya), and other ISO 4217 currencies.</p>
 *
 * @author MANFOUO Braun
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Money(BigDecimal amount, String currency) {

    public Money {
        Objects.requireNonNull(amount, "amount is required");
        Objects.requireNonNull(currency, "currency is required");
        if (currency.isBlank()) throw new IllegalArgumentException("currency must not be blank");
        amount = amount.setScale(2, RoundingMode.HALF_UP);
    }

    // ─── Factory methods ─────────────────────────────────────────────────────

    public static Money of(BigDecimal amount, String currency) {
        return new Money(amount, currency);
    }

    public static Money of(double amount, String currency) {
        return new Money(BigDecimal.valueOf(amount), currency);
    }

    public static Money of(long amount, String currency) {
        return new Money(BigDecimal.valueOf(amount), currency);
    }

    public static Money zero(String currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    public static Money xaf(BigDecimal amount) { return new Money(amount, "XAF"); }
    public static Money xaf(long amount)       { return new Money(BigDecimal.valueOf(amount), "XAF"); }
    public static Money ngn(BigDecimal amount) { return new Money(amount, "NGN"); }
    public static Money kes(BigDecimal amount) { return new Money(amount, "KES"); }

    // ─── Arithmetic ──────────────────────────────────────────────────────────

    public Money add(Money other) {
        assertSameCurrency(other);
        return new Money(this.amount.add(other.amount), this.currency);
    }

    public Money subtract(Money other) {
        assertSameCurrency(other);
        return new Money(this.amount.subtract(other.amount), this.currency);
    }

    public Money multiply(BigDecimal factor) {
        return new Money(this.amount.multiply(factor).setScale(2, RoundingMode.HALF_UP), this.currency);
    }

    public Money multiply(double factor) {
        return multiply(BigDecimal.valueOf(factor));
    }

    public Money percentage(BigDecimal pct) {
        return multiply(pct.divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP));
    }

    public Money percentage(double pct) {
        return percentage(BigDecimal.valueOf(pct));
    }

    // ─── Predicates ──────────────────────────────────────────────────────────

    @JsonIgnore public boolean isNegative() { return amount.compareTo(BigDecimal.ZERO) < 0; }
    @JsonIgnore public boolean isZero()     { return amount.compareTo(BigDecimal.ZERO) == 0; }
    @JsonIgnore public boolean isPositive() { return amount.compareTo(BigDecimal.ZERO) > 0; }

    public boolean isGreaterThan(Money other) {
        assertSameCurrency(other);
        return this.amount.compareTo(other.amount) > 0;
    }

    public boolean isLessThan(Money other) {
        assertSameCurrency(other);
        return this.amount.compareTo(other.amount) < 0;
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private void assertSameCurrency(Money other) {
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                    "Cannot operate on different currencies: " + this.currency + " vs " + other.currency);
        }
    }

    @Override
    public String toString() {
        return amount.toPlainString() + " " + currency;
    }
}
