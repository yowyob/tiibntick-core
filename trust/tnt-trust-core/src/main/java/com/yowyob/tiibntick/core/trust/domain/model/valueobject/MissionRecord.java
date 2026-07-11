package com.yowyob.tiibntick.core.trust.domain.model.valueobject;

import com.yowyob.tiibntick.core.trust.domain.model.enums.LogisticTrustEventType;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Value Object — {@code MissionRecord}.
 *
 * <p>Represents an immutable reference to a TiiBnTick delivery mission
 * anchored on the Hyperledger Fabric ledger. Missions are recorded on-chain
 * at three lifecycle points:
 * <ol>
 *   <li>{@code MISSION_CREATED_ON_CHAIN} — provides proof of assignment</li>
 *   <li>{@code MISSION_COMPLETED_ON_CHAIN} — immutable completion evidence</li>
 *   <li>{@code MISSION_CANCELLED_ON_CHAIN} — cancellation audit record</li>
 * </ol>
 *
 * <p>On-chain mission records serve as the authoritative reference for
 * dispute resolution: if a deliverer claims they completed a mission, the
 * Fabric ledger record is the single source of truth.
 *
 * <p><strong>No Spring annotations.</strong> Pure domain code.
 *
 * @author MANFOUO Braun
 * @version 1.0
 */
public final class MissionRecord {

    private final String missionId;
    private final String tenantId;
    private final String actorId;

    /** Number of packages assigned to this mission. */
    private final int packageCount;

    /** The lifecycle event type anchored on-chain for this record. */
    private final LogisticTrustEventType eventType;

    /** Optional cancellation reason — populated only for MISSION_CANCELLED_ON_CHAIN. */
    private final String cancelReason;

    private final LocalDateTime occurredAt;

    /**
     * Fabric tx hash — populated after the mission event is committed on-chain.
     */
    private String blockchainTxHash;

    public MissionRecord(
            final String missionId,
            final String tenantId,
            final String actorId,
            final int packageCount,
            final LogisticTrustEventType eventType,
            final String cancelReason,
            final LocalDateTime occurredAt) {
        this.missionId = Objects.requireNonNull(missionId, "missionId must not be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.actorId = actorId;
        this.packageCount = packageCount;
        this.eventType = Objects.requireNonNull(eventType, "eventType must not be null");
        this.cancelReason = cancelReason;
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }

    // ── Factory Methods ───────────────────────────────────────────────────────

    /**
     * Creates a mission creation record.
     *
     * @param missionId    the mission identifier
     * @param tenantId     the tenant identifier
     * @param actorId      the assigned deliverer actor
     * @param packageCount the number of packages
     */
    public static MissionRecord created(
            final String missionId, final String tenantId,
            final String actorId, final int packageCount) {
        return new MissionRecord(missionId, tenantId, actorId, packageCount,
                LogisticTrustEventType.MISSION_CREATED_ON_CHAIN, null, LocalDateTime.now());
    }

    /**
     * Creates a mission completion record.
     *
     * @param missionId the mission identifier
     * @param tenantId  the tenant identifier
     * @param actorId   the deliverer who completed the mission
     */
    public static MissionRecord completed(
            final String missionId, final String tenantId, final String actorId) {
        return new MissionRecord(missionId, tenantId, actorId, 0,
                LogisticTrustEventType.MISSION_COMPLETED_ON_CHAIN, null, LocalDateTime.now());
    }

    /**
     * Creates a mission cancellation record.
     *
     * @param missionId    the mission identifier
     * @param tenantId     the tenant identifier
     * @param cancelReason the reason for cancellation
     */
    public static MissionRecord cancelled(
            final String missionId, final String tenantId, final String cancelReason) {
        return new MissionRecord(missionId, tenantId, null, 0,
                LogisticTrustEventType.MISSION_CANCELLED_ON_CHAIN, cancelReason, LocalDateTime.now());
    }

    // ── Domain Behavior ───────────────────────────────────────────────────────

    /** Records the Fabric tx hash after on-chain confirmation. */
    public void confirmOnChain(final String txHash) {
        this.blockchainTxHash = Objects.requireNonNull(txHash);
    }

    /** Returns {@code true} if this record has been confirmed on-chain. */
    public boolean isOnChain() {
        return blockchainTxHash != null && !blockchainTxHash.isBlank();
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public String getMissionId() { return missionId; }
    public String getTenantId() { return tenantId; }
    public String getActorId() { return actorId; }
    public int getPackageCount() { return packageCount; }
    public LogisticTrustEventType getEventType() { return eventType; }
    public String getCancelReason() { return cancelReason; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
    public String getBlockchainTxHash() { return blockchainTxHash; }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof MissionRecord other)) return false;
        return Objects.equals(missionId, other.missionId)
                && Objects.equals(eventType, other.eventType);
    }

    @Override
    public int hashCode() { return Objects.hash(missionId, eventType); }
}
