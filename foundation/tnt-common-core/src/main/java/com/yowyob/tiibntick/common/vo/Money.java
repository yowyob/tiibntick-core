package com.yowyob.tiibntick.common.vo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/**
 * Immutable value object representing a monetary amount in a specific currency.
 *
 * <p>All arithmetic operations return new {@code Money} instances and enforce that
 * both operands share the same currency. Rounding uses {@link RoundingMode#HALF_UP}
 * and a scale of 2 decimal places for most currencies (configurable for currencies like
 * the Kuwaiti Dinar which use 3 decimal places).
 *
 * <p>Primary currencies used in TiiBnTick:
 * <ul>
 *   <li>XAF — Central African CFA franc (no decimals by ISO 4217)</li>
 *   <li>NGN — Nigerian Naira</li>
 *   <li>KES — Kenyan Shilling</li>
 *   <li>USD — US Dollar</li>
 *   <li>EUR — Euro</li>
 * </ul>
 *
 * Author: MANFOUO Braun
 */
public final class Money {

    /** Zero XAF singleton — useful as a neutral element. */
    public static final Money ZERO_XAF = of(BigDecimal.ZERO, "XAF");

    private final BigDecimal amount;
    private final Currency currency;

    private Money(BigDecimal amount, Currency currency) {
        this.currency = Objects.requireNonNull(currency, "currency is required");
        int scale = currency.getDefaultFractionDigits() < 0 ? 2 : currency.getDefaultFractionDigits();
        this.amount = Objects.requireNonNull(amount, "amount is required")
                .setScale(scale, RoundingMode.HALF_UP);
    }

    /**
     * Creates a {@code Money} from a {@link BigDecimal} and ISO 4217 currency code.
     *
     * @param amount       monetary amount — may be negative (for refunds etc.)
     * @param currencyCode ISO 4217 code, e.g. "XAF", "USD"
     * @throws IllegalArgumentException if {@code currencyCode} is unknown
     */
    public static Money of(BigDecimal amount, String currencyCode) {
        return new Money(amount, Currency.getInstance(currencyCode));
    }

    /**
     * Creates a {@code Money} from a long (number of minor units, e.g. centimes).
     * For XAF (0 decimals) this is the exact amount in francs.
     */
    public static Money of(long amount, String currencyCode) {
        return of(BigDecimal.valueOf(amount), currencyCode);
    }

    /**
     * Creates a {@code Money} from a {@link Currency} directly.
     */
    public static Money of(BigDecimal amount, Currency currency) {
        return new Money(amount, currency);
    }

    // ── Arithmetic ────────────────────────────────────────────────────────

    /**
     * Returns the sum of this money and {@code other}.
     *
     * @throws IllegalArgumentException if currencies differ
     */
    public Money add(Money other) {
        assertSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    /**
     * Returns the difference of this money and {@code other}.
     *
     * @throws IllegalArgumentException if currencies differ
     */
    public Money subtract(Money other) {
        assertSameCurrency(other);
        return new Money(amount.subtract(other.amount), currency);
    }

    /**
     * Returns this money multiplied by {@code factor}, rounded HALF_UP.
     */
    public Money multiply(BigDecimal factor) {
        Objects.requireNonNull(factor, "factor is required");
        return new Money(amount.multiply(factor), currency);
    }

    /**
     * Returns this money multiplied by a plain {@code double} factor.
     */
    public Money multiply(double factor) {
        return multiply(BigDecimal.valueOf(factor));
    }

    /**
     * Returns this money divided by {@code divisor}, rounded HALF_UP.
     */
    public Money divide(BigDecimal divisor) {
        Objects.requireNonNull(divisor, "divisor is required");
        if (divisor.compareTo(BigDecimal.ZERO) == 0) {
            throw new ArithmeticException("Division by zero");
        }
        int scale = currency.getDefaultFractionDigits() < 0 ? 2 : currency.getDefaultFractionDigits();
        return new Money(amount.divide(divisor, scale, RoundingMode.HALF_UP), currency);
    }

    // ── Comparison ────────────────────────────────────────────────────────

    public boolean isGreaterThan(Money other) {
        assertSameCurrency(other);
        return amount.compareTo(other.amount) > 0;
    }

    public boolean isGreaterThanOrEqualTo(Money other) {
        assertSameCurrency(other);
        return amount.compareTo(other.amount) >= 0;
    }

    public boolean isLessThan(Money other) {
        assertSameCurrency(other);
        return amount.compareTo(other.amount) < 0;
    }

    public boolean isNegative()    { return amount.signum() < 0; }
    public boolean isZero()        { return amount.signum() == 0; }
    public boolean isPositive()    { return amount.signum() > 0; }

    /** Returns the absolute value of this money. */
    public Money abs() {
        return new Money(amount.abs(), currency);
    }

    /** Returns the negated value of this money (useful for debit/credit representation). */
    public Money negate() {
        return new Money(amount.negate(), currency);
    }

    // ── Accessors ─────────────────────────────────────────────────────────

    public BigDecimal getAmount()    { return amount; }
    public Currency getCurrency()    { return currency; }
    public String getCurrencyCode()  { return currency.getCurrencyCode(); }

    // ── Private helpers ───────────────────────────────────────────────────

    private void assertSameCurrency(Money other) {
        Objects.requireNonNull(other, "other money must not be null");
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                    "Currency mismatch: cannot operate on " + currency + " and " + other.currency);
        }
    }

    // ── Object contract ───────────────────────────────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Money other)) return false;
        return amount.compareTo(other.amount) == 0 && currency.equals(other.currency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount.stripTrailingZeros(), currency);
    }

    @Override
    public String toString() {
        return amount.toPlainString() + " " + currency.getCurrencyCode();
    }
}
