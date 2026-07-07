package com.yowyob.tiibntick.core.billing.dsl.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;

/**
 * Immutable monetary value with currency.
 * <p>
 * All arithmetic operations preserve currency homogeneity: mixing currencies
 * throws {@link IllegalArgumentException}. Values are always rounded to
 * the currency's default fraction digits (0 for XAF/XOF per ISO 4217).
 * </p>
 *
 * @author MANFOUO Braun
 */
@Value
@JsonIgnoreProperties(ignoreUnknown = true)
public class Money {

    BigDecimal amount;
    Currency currency;

    private Money(BigDecimal amount, Currency currency) {
        this.currency = currency;
        // round to the currency's scale (XAF has 0 fractional digits)
        int scale = Math.max(currency.getDefaultFractionDigits(), 0);
        this.amount = amount.setScale(scale, RoundingMode.HALF_UP);
    }

    /** Factory — preferred constructor. Also used as Jackson deserializer entry point. */
    @JsonCreator
    public static Money of(
            @JsonProperty("amount") BigDecimal amount,
            @JsonProperty("currency") String currencyCode) {
        return new Money(amount, Currency.getInstance(currencyCode));
    }

    /** Factory — convenience for integer amounts (common for XAF). */
    public static Money of(long amount, String currencyCode) {
        return of(BigDecimal.valueOf(amount), currencyCode);
    }

    /** Factory — convenience for double (rounds to currency scale). */
    public static Money of(double amount, String currencyCode) {
        return of(BigDecimal.valueOf(amount), currencyCode);
    }

    /** Zero amount in XAF (Central African CFA franc). */
    public static Money zeroXAF() {
        return of(BigDecimal.ZERO, "XAF");
    }

    public Money add(Money other) {
        assertSameCurrency(other);
        return new Money(this.amount.add(other.amount), this.currency);
    }

    public Money subtract(Money other) {
        assertSameCurrency(other);
        return new Money(this.amount.subtract(other.amount), this.currency);
    }

    public Money multiply(BigDecimal factor) {
        return new Money(this.amount.multiply(factor), this.currency);
    }

    public Money multiply(double factor) {
        return multiply(BigDecimal.valueOf(factor));
    }

    /**
     * Applies a percentage to this amount.
     * Example: {@code money.percentage(15)} → {@code money * 0.15}.
     *
     * @param percentage percentage value (e.g. 15 for 15%)
     * @return computed fraction of this amount
     */
    public Money percentage(BigDecimal percentage) {
        return multiply(percentage.divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP));
    }

    public Money percentage(double percentage) {
        return percentage(BigDecimal.valueOf(percentage));
    }

    @JsonIgnore
    public boolean isNegative() {
        return amount.compareTo(BigDecimal.ZERO) < 0;
    }

    @JsonIgnore
    public boolean isZero() {
        return amount.compareTo(BigDecimal.ZERO) == 0;
    }

    @JsonIgnore
    public boolean isPositive() {
        return amount.compareTo(BigDecimal.ZERO) > 0;
    }

    public int compareTo(Money other) {
        assertSameCurrency(other);
        return this.amount.compareTo(other.amount);
    }

    @Override
    public String toString() {
        return amount.toPlainString() + " " + currency.getCurrencyCode();
    }

    private void assertSameCurrency(Money other) {
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                    "Cannot operate on different currencies: " + this.currency + " vs " + other.currency);
        }
    }
}
