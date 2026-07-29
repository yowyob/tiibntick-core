package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO exposing the full subscription state of a delivery person.
 *
 * Fields:
 *   - Plan info      : type, status, price, paymentMethod
 *   - Quota info     : maxDeliveries (-1 = unlimited), deliveriesUsed, deliveriesRemaining, resetDate
 *   - Commission     : commissionPercent (what TiiBnTick takes), netPercent (what the livreur keeps)
 *   - Validity dates : startDate, endDate
 *
 * @author TiiBnTickTeam
 * @date 08/07/2026
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionStatusResponseDTO {

    // ── Identity ──────────────────────────────────────────────────
    private UUID subscriptionId;
    private UUID freelancerId;

    // ── Plan ──────────────────────────────────────────────────────
    /** FREE | STANDARD | ADVANCE */
    private String plan;

    /** ACTIVE | PENDING | SUSPENDED | EXPIRED | CANCELLED */
    private String status;

    /** Price paid for this subscription (FCFA) */
    private Float price;

    /** Payment method used (MOBILE_MONEY, ORANGE_MONEY, CASH) */
    private String paymentMethod;

    // ── Quota ─────────────────────────────────────────────────────
    /** Maximum deliveries allowed this period. -1 means unlimited (ADVANCE plan). */
    private int maxDeliveries;

    /** Whether the plan has an unlimited quota */
    private boolean unlimited;

    /** Number of deliveries completed in the current billing period */
    private int deliveriesUsed;

    /**
     * Remaining deliveries this period.
     * Returns -1 for unlimited plans (ADVANCE).
     */
    private int deliveriesRemaining;

    /** Percentage of quota used this period (0–100). Always 0 for unlimited plans. */
    private int quotaUsagePercent;

    /** Date when deliveriesUsed will be reset to 0 (first day of next month). Null for ADVANCE. */
    private Instant resetDate;

    // ── Commission ────────────────────────────────────────────────
    /** Percentage of each delivery price retained by TiiBnTick */
    private double commissionPercent;

    /** Percentage of each delivery price transferred to the delivery person */
    private double netPercent;

    // ── Validity ──────────────────────────────────────────────────
    private Instant startDate;
    private Instant endDate;
}
