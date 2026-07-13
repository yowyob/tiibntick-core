package com.yowyob.tiibntick.core.marketback.domain.model;

import java.util.Objects;

/**
 * Value Object representing a monetary amount in a specific currency.
 * Amounts are stored as long (minor units, e.g. centimes for XAF).
 *
 * @author MANFOUO Braun
 */
public record Money(long amount, String currency) {

    /** Factory method for XAF (Central African CFA franc). */
    public static Money ofXaf(long amount) {
        return new Money(amount, "XAF");
    }

    public static Money zero(String currency) {
        return new Money(0L, currency);
    }

    public static Money zeroXaf() {
        return new Money(0L, "XAF");
    }

    public Money add(Money other) {
        assertSameCurrency(other);
        return new Money(this.amount + other.amount, this.currency);
    }

    public Money subtract(Money other) {
        assertSameCurrency(other);
        return new Money(this.amount - other.amount, this.currency);
    }

    public Money multiply(double factor) {
        return new Money(Math.round(this.amount * factor), this.currency);
    }

    public Money applyDiscount(double discountPercent) {
        double factor = 1.0 - (discountPercent / 100.0);
        return multiply(factor);
    }

    public boolean isGreaterThan(Money other) {
        assertSameCurrency(other);
        return this.amount > other.amount;
    }

    public boolean isLessThan(Money other) {
        assertSameCurrency(other);
        return this.amount < other.amount;
    }

    public boolean isZero() {
        return this.amount == 0;
    }

    public String formatted() {
        return String.format("%,d %s", amount, currency);
    }

    private void assertSameCurrency(Money other) {
        if (!Objects.equals(this.currency, other.currency)) {
            throw new IllegalArgumentException(
                "Cannot operate on different currencies: " + this.currency + " vs " + other.currency);
        }
    }
}
