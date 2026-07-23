package com.yowyob.tiibntick.core.trust.domain.model.valueobject;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Value Object — {@code DaoRuleRecord}.
 *
 * <p>Represents a DAO zone collective governance rule activation, anchored
 * on the Hyperledger Fabric ledger. Once activated on-chain, the rule is
 * immutable and serves as the authoritative reference for zone governance
 * decisions.
 *
 * <p><strong>No Spring annotations.</strong> Pure domain code.
 *
 * @author MANFOUO Braun
 * @version 1.0
 */
public final class DaoRuleRecord {

    private final String ruleId;
    private final String zoneId;
    private final String tenantId;

    /** JSON-encoded rule definition. */
    private final String ruleJson;

    private final LocalDateTime activatedAt;

    /**
     * Fabric transaction hash — populated asynchronously after
     * the {@code DAO_RULE_ACTIVATED} event is committed to the ledger.
     */
    private String blockchainTxHash;

    private DaoRuleRecord(
            final String ruleId,
            final String zoneId,
            final String tenantId,
            final String ruleJson,
            final LocalDateTime activatedAt,
            final String blockchainTxHash) {
        this.ruleId = Objects.requireNonNull(ruleId, "ruleId must not be null");
        this.zoneId = Objects.requireNonNull(zoneId, "zoneId must not be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.ruleJson = Objects.requireNonNull(ruleJson, "ruleJson must not be null");
        this.activatedAt = Objects.requireNonNull(activatedAt, "activatedAt must not be null");
        this.blockchainTxHash = blockchainTxHash;
    }

    // ── Factory Methods ───────────────────────────────────────────────────────

    /**
     * Creates a new {@link DaoRuleRecord} as activated (not yet confirmed on-chain).
     *
     * @param zoneId   the DAO zone identifier
     * @param tenantId the tenant identifier
     * @param ruleJson the JSON-encoded rule definition
     * @return a new {@link DaoRuleRecord} pending on-chain confirmation
     */
    public static DaoRuleRecord activate(
            final String zoneId,
            final String tenantId,
            final String ruleJson) {
        return new DaoRuleRecord(
                UUID.randomUUID().toString(),
                zoneId, tenantId, ruleJson, LocalDateTime.now(), null);
    }

    /**
     * Reconstitutes a {@link DaoRuleRecord} from persisted state.
     */
    public static DaoRuleRecord reconstitute(
            final String ruleId,
            final String zoneId,
            final String tenantId,
            final String ruleJson,
            final LocalDateTime activatedAt,
            final String blockchainTxHash) {
        return new DaoRuleRecord(ruleId, zoneId, tenantId, ruleJson, activatedAt, blockchainTxHash);
    }

    // ── Domain Behavior ───────────────────────────────────────────────────────

    /**
     * Records the Fabric transaction hash after on-chain confirmation.
     *
     * @param txHash the Fabric tx hash confirming this rule on the ledger
     */
    public void confirmOnChain(final String txHash) {
        Objects.requireNonNull(txHash, "txHash must not be null");
        this.blockchainTxHash = txHash;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public String getRuleId() { return ruleId; }
    public String getZoneId() { return zoneId; }
    public String getTenantId() { return tenantId; }
    public String getRuleJson() { return ruleJson; }
    public LocalDateTime getActivatedAt() { return activatedAt; }
    public String getBlockchainTxHash() { return blockchainTxHash; }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof DaoRuleRecord other)) return false;
        return Objects.equals(ruleId, other.ruleId);
    }

    @Override
    public int hashCode() { return Objects.hash(ruleId); }

    @Override
    public String toString() {
        return "DaoRuleRecord{ruleId='" + ruleId + "', zoneId='" + zoneId + "'}";
    }
}
