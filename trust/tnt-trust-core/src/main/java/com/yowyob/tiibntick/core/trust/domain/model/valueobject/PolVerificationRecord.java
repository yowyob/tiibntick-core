package com.yowyob.tiibntick.core.trust.domain.model.valueobject;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Value Object — {@code PolVerificationRecord}.
 *
 * <p>Represents a verified Proof-of-Location (PoL) event — cryptographic
 * evidence that a deliverer was physically present at a specific GPS
 * location at a specific time — anchored on the Hyperledger Fabric ledger.
 *
 * <p><strong>No Spring annotations.</strong> Pure domain code.
 *
 * @author MANFOUO Braun
 * @version 1.0
 */
public final class PolVerificationRecord {

    private final String eventId;
    private final String actorId;
    private final String tenantId;
    private final double gpsLat;
    private final double gpsLng;

    /** SHA-256 hash of the Proof-of-Location payload from the mobile client. */
    private final String polHash;

    private final LocalDateTime verifiedAt;

    /**
     * Fabric transaction hash — populated asynchronously after
     * the {@code PROOF_OF_LOCATION_VERIFIED} event is committed to the ledger.
     */
    private String blockchainTxHash;

    private PolVerificationRecord(
            final String eventId,
            final String actorId,
            final String tenantId,
            final double gpsLat,
            final double gpsLng,
            final String polHash,
            final LocalDateTime verifiedAt,
            final String blockchainTxHash) {
        this.eventId = Objects.requireNonNull(eventId, "eventId must not be null");
        this.actorId = Objects.requireNonNull(actorId, "actorId must not be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.gpsLat = gpsLat;
        this.gpsLng = gpsLng;
        this.polHash = Objects.requireNonNull(polHash, "polHash must not be null");
        this.verifiedAt = Objects.requireNonNull(verifiedAt, "verifiedAt must not be null");
        this.blockchainTxHash = blockchainTxHash;
    }

    // ── Factory Methods ───────────────────────────────────────────────────────

    /**
     * Creates a new {@link PolVerificationRecord} (not yet confirmed on-chain).
     *
     * @param actorId  the deliverer whose location is being proven
     * @param tenantId the tenant identifier
     * @param gpsLat   the verified GPS latitude
     * @param gpsLng   the verified GPS longitude
     * @param polHash  the SHA-256 hash of the PoL payload from the mobile app
     * @return a new {@link PolVerificationRecord} pending on-chain confirmation
     */
    public static PolVerificationRecord verify(
            final String actorId,
            final String tenantId,
            final double gpsLat,
            final double gpsLng,
            final String polHash) {
        return new PolVerificationRecord(
                UUID.randomUUID().toString(),
                actorId, tenantId, gpsLat, gpsLng, polHash, LocalDateTime.now(), null);
    }

    /**
     * Reconstitutes a {@link PolVerificationRecord} from persisted state.
     */
    public static PolVerificationRecord reconstitute(
            final String eventId,
            final String actorId,
            final String tenantId,
            final double gpsLat,
            final double gpsLng,
            final String polHash,
            final LocalDateTime verifiedAt,
            final String blockchainTxHash) {
        return new PolVerificationRecord(eventId, actorId, tenantId, gpsLat, gpsLng, polHash,
                verifiedAt, blockchainTxHash);
    }

    // ── Domain Behavior ───────────────────────────────────────────────────────

    /**
     * Records the Fabric transaction hash after on-chain confirmation.
     *
     * @param txHash the Fabric tx hash confirming this PoL on the ledger
     */
    public void confirmOnChain(final String txHash) {
        Objects.requireNonNull(txHash, "txHash must not be null");
        this.blockchainTxHash = txHash;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public String getEventId() { return eventId; }
    public String getActorId() { return actorId; }
    public String getTenantId() { return tenantId; }
    public double getGpsLat() { return gpsLat; }
    public double getGpsLng() { return gpsLng; }
    public String getPolHash() { return polHash; }
    public LocalDateTime getVerifiedAt() { return verifiedAt; }
    public String getBlockchainTxHash() { return blockchainTxHash; }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof PolVerificationRecord other)) return false;
        return Objects.equals(eventId, other.eventId);
    }

    @Override
    public int hashCode() { return Objects.hash(eventId); }

    @Override
    public String toString() {
        return "PolVerificationRecord{eventId='" + eventId + "', actorId='" + actorId + "'}";
    }
}
