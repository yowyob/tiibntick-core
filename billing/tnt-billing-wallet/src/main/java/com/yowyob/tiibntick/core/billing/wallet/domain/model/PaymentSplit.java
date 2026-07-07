package com.yowyob.tiibntick.core.billing.wallet.domain.model;

import com.yowyob.tiibntick.core.billing.wallet.domain.enums.TransactionStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * PaymentSplit — domain entity recording how mission revenue is distributed
 * between the platform, the FreelancerOrg, and an optional sub-deliverer.
 *
 * <p>Revenue split model for a FreelancerOrg mission:
 * <pre>
 *   totalAmount = platformCommission + orgRevenue + subDelivererCommission
 * </pre>
 *
 * <p>The split is persisted in {@code wallet_payment_splits} and triggers
 * wallet credit operations for each recipient.
 *
 * <p>Reference: {@code 04_TNT_Modules_Impacted.md} §5.5 — tnt-billing-wallet
 *
 * @author MANFOUO Braun
 */
@Getter
@Builder
public class PaymentSplit {

    private final UUID id;

    /** The delivery mission ID this split belongs to. */
    private final String missionId;

    /** Total amount paid by the client for this mission (XAF). */
    private final BigDecimal totalAmount;

    /** Currency code (always XAF in Cameroonian context). */
    @Builder.Default
    private final String currency = "XAF";

    /** Platform commission amount (3–5% of totalAmount, configurable). */
    private final BigDecimal platformCommission;

    /**
     * Amount credited to the FreelancerOrg's wallet.
     * = totalAmount - platformCommission - subDelivererCommission.
     */
    private final BigDecimal orgRevenue;

    /**
     * Commission credited to the sub-deliverer (if applicable).
     * Null when the OWNER executes the mission directly.
     */
    private final BigDecimal subDelivererCommission;

    /**
     * UUID of the sub-deliverer receiving the commission.
     * References tnt-actor-core UUID — pure integration key.
     * Null when role = OWNER.
     */
    private final String subDelivererId;

    /** Processing status: PENDING → EXECUTED | FAILED. */
    @Builder.Default
    private TransactionStatus status = TransactionStatus.PENDING;

    /** Timestamp when the split was successfully executed. */
    private LocalDateTime executedAt;

    private final LocalDateTime createdAt;

    // ── Factory ──────────────────────────────────────────────────────────

    /**
     * Creates a new PENDING PaymentSplit for a FreelancerOrg mission.
     *
     * @param missionId             delivery mission ID
     * @param totalAmount           total amount from client
     * @param platformCommissionPct platform commission rate (0.0–1.0)
     * @param orgId                 FreelancerOrg UUID
     * @param subDelivererId        sub-deliverer UUID (null if OWNER)
     * @param subDelivererCommPct   sub-deliverer commission rate (0.0–1.0)
     * @return new PaymentSplit
     */
    public static PaymentSplit createForFreelancerOrg(
            String missionId,
            BigDecimal totalAmount,
            double platformCommissionPct,
            String orgId,
            String subDelivererId,
            double subDelivererCommPct) {

        Objects.requireNonNull(missionId, "missionId is required");
        Objects.requireNonNull(totalAmount, "totalAmount is required");
        if (totalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("totalAmount must be positive");
        }

        BigDecimal platform = totalAmount.multiply(BigDecimal.valueOf(platformCommissionPct))
                .setScale(2, java.math.RoundingMode.HALF_UP);

        BigDecimal subComm = subDelivererId != null
                ? totalAmount.multiply(BigDecimal.valueOf(subDelivererCommPct))
                        .setScale(2, java.math.RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        BigDecimal orgRev = totalAmount.subtract(platform).subtract(subComm);

        return PaymentSplit.builder()
                .id(UUID.randomUUID())
                .missionId(missionId)
                .totalAmount(totalAmount)
                .currency("XAF")
                .platformCommission(platform)
                .orgRevenue(orgRev)
                .subDelivererCommission(subComm.compareTo(BigDecimal.ZERO) > 0 ? subComm : null)
                .subDelivererId(subDelivererId)
                .status(TransactionStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();
    }

    /** Marks the split as EXECUTED. */
    public void markExecuted() {
        this.status = TransactionStatus.CONFIRMED;
        this.executedAt = LocalDateTime.now();
    }

    /** Marks the split as FAILED. */
    public void markFailed() {
        this.status = TransactionStatus.FAILED;
    }
}
