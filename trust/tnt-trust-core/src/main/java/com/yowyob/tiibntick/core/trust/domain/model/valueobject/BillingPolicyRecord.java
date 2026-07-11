package com.yowyob.tiibntick.core.trust.domain.model.valueobject;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Value Object — {@code BillingPolicyRecord}.
 *
 * <p>Represents the immutable on-chain record of a billing policy activation
 * in TiiBnTick. When an agency activates a new pricing policy, the event is
 * anchored on Hyperledger Fabric to provide:
 * <ul>
 *   <li>Immutable proof of the tariff terms in effect at a given time</li>
 *   <li>Dispute resolution reference for pricing disagreements</li>
 *   <li>Audit trail for regulatory compliance (OHADA framework)</li>
 * </ul>
 *
 * <p><strong>No Spring annotations.</strong> Pure domain code.
 *
 * @author MANFOUO Braun
 * @version 1.0
 */
public final class BillingPolicyRecord {

    private final String recordId;
    private final String policyId;
    private final String agencyId;
    private final String tenantId;

    /**
     * JSON-serialized summary of the policy terms activated.
     * Contains key pricing rule identifiers (not the full DSL expression
     * for size reasons — the full policy is stored in {@code tnt-billing-pricing}).
     */
    private final String policySummaryJson;

    private final LocalDateTime activatedAt;

    /**
     * Fabric tx hash — populated after the {@code BILLING_POLICY_ACTIVATED}
     * event is committed to the ledger.
     */
    private String blockchainTxHash;

    public BillingPolicyRecord(
            final String recordId,
            final String policyId,
            final String agencyId,
            final String tenantId,
            final String policySummaryJson,
            final LocalDateTime activatedAt) {
        this.recordId = Objects.requireNonNull(recordId, "recordId must not be null");
        this.policyId = Objects.requireNonNull(policyId, "policyId must not be null");
        this.agencyId = Objects.requireNonNull(agencyId, "agencyId must not be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.policySummaryJson = policySummaryJson;
        this.activatedAt = Objects.requireNonNull(activatedAt, "activatedAt must not be null");
    }

    // ── Factory Method ────────────────────────────────────────────────────────

    /**
     * Creates a new {@link BillingPolicyRecord} at the moment of policy activation.
     *
     * @param policyId          the billing policy identifier
     * @param agencyId          the agency activating the policy
     * @param tenantId          the tenant identifier
     * @param policySummaryJson JSON summary of the activated pricing rules
     * @return a new record ready for blockchain anchoring
     */
    public static BillingPolicyRecord activate(
            final String policyId,
            final String agencyId,
            final String tenantId,
            final String policySummaryJson) {
        return new BillingPolicyRecord(
                UUID.randomUUID().toString(),
                policyId, agencyId, tenantId,
                policySummaryJson,
                LocalDateTime.now());
    }

    // ── Domain Behavior ───────────────────────────────────────────────────────

    /** Records the Fabric tx hash after on-chain confirmation. */
    public void confirmOnChain(final String txHash) {
        this.blockchainTxHash = Objects.requireNonNull(txHash);
    }

    /**
     * Returns {@code true} if this billing policy activation
     * has been confirmed on the Hyperledger Fabric ledger.
     */
    public boolean wasRecordedOnChain() {
        return blockchainTxHash != null && !blockchainTxHash.isBlank();
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public String getRecordId() { return recordId; }
    public String getPolicyId() { return policyId; }
    public String getAgencyId() { return agencyId; }
    public String getTenantId() { return tenantId; }
    public String getPolicySummaryJson() { return policySummaryJson; }
    public LocalDateTime getActivatedAt() { return activatedAt; }
    public String getBlockchainTxHash() { return blockchainTxHash; }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof BillingPolicyRecord other)) return false;
        return Objects.equals(recordId, other.recordId);
    }

    @Override
    public int hashCode() { return Objects.hash(recordId); }

    @Override
    public String toString() {
        return "BillingPolicyRecord{policyId='" + policyId + "', agencyId='" + agencyId
                + "', onChain=" + wasRecordedOnChain() + "}";
    }
}
