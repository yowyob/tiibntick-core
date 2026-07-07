package com.yowyob.tiibntick.core.billing.pricing.domain.model;

import com.yowyob.tiibntick.core.billing.dsl.domain.model.Money;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.PackageType;
import lombok.Builder;
import lombok.extern.jackson.Jacksonized;
import lombok.Value;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Fee rule for parcel storage at a Hub Point relay.
 *
 * <p>Pricing is applied per interval (e.g. every 24 hours) after an optional free
 * period. The fee may be restricted to specific package types.
 *
 * <p>Example: 0–24h free, then 500 XAF per 24h interval; only for PERISHABLE and FRAGILE.
 *
 * @author MANFOUO Braun
 */
@Value
@Jacksonized
@Builder
public class HubStorageRule {

    UUID id;

    /** Minimum storage hours covered by this rule (inclusive). Usually 0. */
    int minHours;

    /**
     * Maximum storage hours covered by this rule (inclusive).
     * 0 or -1 means no upper bound.
     */
    int maxHours;

    /** Fee charged per {@link #intervalHours} interval. In XAF. */
    BigDecimal feePerInterval;

    /** Duration of each billable storage interval in hours (e.g. 24 for daily). */
    int intervalHours;

    /**
     * Whether the first interval is free (no charge if storage ≤ intervalHours).
     * When true, billing starts from the second interval.
     */
    boolean isFreePeriod;

    /**
     * Package types to which this rule applies.
     * Null or empty means all package types are surchargeble.
     */
    List<PackageType> applicablePackageTypes;

    /**
     * Computes the storage fee for a parcel stored for {@code storageHours} hours.
     *
     * <p>Calculation:
     * <pre>
     *   billableIntervals = floor(storageHours / intervalHours) - (isFreePeriod ? 1 : 0)
     *   fee = max(0, billableIntervals) × feePerInterval
     * </pre>
     *
     * @param storageHours  total hours the parcel has been stored
     * @param currency      ISO 4217 currency code for the result
     * @return computed storage fee (may be zero if within the free period)
     */
    public Money computeFee(int storageHours, String currency) {
        if (storageHours <= 0 || feePerInterval == null || intervalHours <= 0) {
            return Money.of(BigDecimal.ZERO, currency);
        }
        long totalIntervals = storageHours / intervalHours;
        long billableIntervals = isFreePeriod ? Math.max(0L, totalIntervals - 1) : totalIntervals;
        BigDecimal fee = feePerInterval.multiply(BigDecimal.valueOf(billableIntervals));
        return Money.of(fee, currency);
    }

    /**
     * Returns {@code true} if this rule applies to the given package type.
     *
     * @param packageType the type of the parcel
     * @return {@code true} if applicable or if no restrictions are defined
     */
    public boolean appliesTo(PackageType packageType) {
        if (applicablePackageTypes == null || applicablePackageTypes.isEmpty()) return true;
        return applicablePackageTypes.contains(packageType);
    }

    /**
     * Returns {@code true} if the given storage hour count falls within this rule's range.
     *
     * @param storageHours hours in storage
     * @return true if this rule should be applied
     */
    public boolean covers(int storageHours) {
        if (storageHours < minHours) return false;
        return maxHours <= 0 || storageHours <= maxHours;
    }
}
