package com.yowyob.tiibntick.core.trust.domain.model.valueobject;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Value Object — {@code GeofenceCrossingRecord}.
 *
 * <p>Represents a deliverer actor's crossing (entry or exit) of a geofence
 * zone — a delivery zone, relay hub, danger zone, or any other zone
 * classification — anchored on the Hyperledger Fabric ledger.
 *
 * <p><strong>No Spring annotations.</strong> Pure domain code.
 *
 * @author MANFOUO Braun
 * @version 1.0
 */
public final class GeofenceCrossingRecord {

    private final String crossingId;
    private final String actorId;
    private final String tenantId;
    private final String zoneId;
    private final String zoneName;
    private final String zoneType;

    /** {@code ENTER} or {@code EXIT}. */
    private final String direction;

    private final double gpsLat;
    private final double gpsLng;

    /** The mission in progress at crossing time, if any. */
    private final String missionId;

    private final LocalDateTime occurredAt;

    /**
     * Fabric transaction hash — populated asynchronously after
     * the {@code GEOFENCE_CROSSING_RECORDED} event is committed to the ledger.
     */
    private String blockchainTxHash;

    private GeofenceCrossingRecord(
            final String crossingId,
            final String actorId,
            final String tenantId,
            final String zoneId,
            final String zoneName,
            final String zoneType,
            final String direction,
            final double gpsLat,
            final double gpsLng,
            final String missionId,
            final LocalDateTime occurredAt,
            final String blockchainTxHash) {
        this.crossingId = Objects.requireNonNull(crossingId, "crossingId must not be null");
        this.actorId = Objects.requireNonNull(actorId, "actorId must not be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.zoneId = Objects.requireNonNull(zoneId, "zoneId must not be null");
        this.zoneName = zoneName;
        this.zoneType = zoneType;
        this.direction = Objects.requireNonNull(direction, "direction must not be null");
        this.gpsLat = gpsLat;
        this.gpsLng = gpsLng;
        this.missionId = missionId;
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        this.blockchainTxHash = blockchainTxHash;
    }

    // ── Factory Methods ───────────────────────────────────────────────────────

    /**
     * Creates a new {@link GeofenceCrossingRecord} (not yet confirmed on-chain).
     *
     * @param actorId   the deliverer who crossed the zone boundary
     * @param tenantId  the tenant identifier
     * @param zoneId    the geofence zone identifier
     * @param zoneName  the geofence zone's display name
     * @param zoneType  the zone classification (e.g. RELAY_HUB, DANGER_ZONE)
     * @param direction {@code ENTER} or {@code EXIT}
     * @param gpsLat    latitude at crossing time
     * @param gpsLng    longitude at crossing time
     * @param missionId the mission in progress at crossing time, if any
     * @return a new {@link GeofenceCrossingRecord} pending on-chain confirmation
     */
    public static GeofenceCrossingRecord record(
            final String actorId,
            final String tenantId,
            final String zoneId,
            final String zoneName,
            final String zoneType,
            final String direction,
            final double gpsLat,
            final double gpsLng,
            final String missionId) {
        return new GeofenceCrossingRecord(
                UUID.randomUUID().toString(),
                actorId, tenantId, zoneId, zoneName, zoneType, direction,
                gpsLat, gpsLng, missionId, LocalDateTime.now(), null);
    }

    /**
     * Reconstitutes a {@link GeofenceCrossingRecord} from persisted state.
     */
    public static GeofenceCrossingRecord reconstitute(
            final String crossingId,
            final String actorId,
            final String tenantId,
            final String zoneId,
            final String zoneName,
            final String zoneType,
            final String direction,
            final double gpsLat,
            final double gpsLng,
            final String missionId,
            final LocalDateTime occurredAt,
            final String blockchainTxHash) {
        return new GeofenceCrossingRecord(crossingId, actorId, tenantId, zoneId, zoneName, zoneType,
                direction, gpsLat, gpsLng, missionId, occurredAt, blockchainTxHash);
    }

    // ── Domain Behavior ───────────────────────────────────────────────────────

    /**
     * Records the Fabric transaction hash after on-chain confirmation.
     *
     * @param txHash the Fabric tx hash confirming this crossing on the ledger
     */
    public void confirmOnChain(final String txHash) {
        Objects.requireNonNull(txHash, "txHash must not be null");
        this.blockchainTxHash = txHash;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public String getCrossingId() { return crossingId; }
    public String getActorId() { return actorId; }
    public String getTenantId() { return tenantId; }
    public String getZoneId() { return zoneId; }
    public String getZoneName() { return zoneName; }
    public String getZoneType() { return zoneType; }
    public String getDirection() { return direction; }
    public double getGpsLat() { return gpsLat; }
    public double getGpsLng() { return gpsLng; }
    public String getMissionId() { return missionId; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
    public String getBlockchainTxHash() { return blockchainTxHash; }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof GeofenceCrossingRecord other)) return false;
        return Objects.equals(crossingId, other.crossingId);
    }

    @Override
    public int hashCode() { return Objects.hash(crossingId); }

    @Override
    public String toString() {
        return "GeofenceCrossingRecord{crossingId='" + crossingId + "', actorId='" + actorId
                + "', zoneId='" + zoneId + "', direction='" + direction + "'}";
    }
}
