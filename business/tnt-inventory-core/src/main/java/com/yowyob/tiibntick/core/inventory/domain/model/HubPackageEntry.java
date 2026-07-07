package com.yowyob.tiibntick.core.inventory.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Aggregate Root: HubPackageEntry.
 *
 * TiiBnTick-specific domain concept representing a parcel deposited at a relay hub
 * (TiiBnTick Point). Tracks entry time, storage location, pickup status, and
 * provides overdue detection (parcel left too long without being picked up).
 *
 * This is a critical traceability element linked to TiiBnTick Trust's blockchain proofs.
 *
 * @author MANFOUO Braun.
 */
public final class HubPackageEntry {

    private final UUID id;
    private final UUID tenantId;
    private final UUID hubId;
    private final UUID packageId;
    private final String trackingCode;
    private final String storageLocation;
    private final Instant depositedAt;
    private final Instant pickedUpAt;
    private final UUID depositedByActorId;
    private final UUID pickedUpByActorId;
    private final String recipientPhone;
    private final boolean notified;

    private HubPackageEntry(UUID id, UUID tenantId, UUID hubId, UUID packageId,
                             String trackingCode, String storageLocation,
                             Instant depositedAt, Instant pickedUpAt,
                             UUID depositedByActorId, UUID pickedUpByActorId,
                             String recipientPhone, boolean notified) {
        this.id = Objects.requireNonNull(id);
        this.tenantId = Objects.requireNonNull(tenantId);
        this.hubId = Objects.requireNonNull(hubId);
        this.packageId = Objects.requireNonNull(packageId);
        if (trackingCode == null || trackingCode.isBlank())
            throw new IllegalArgumentException("trackingCode must not be blank");
        this.trackingCode = trackingCode.strip().toUpperCase();
        this.storageLocation = storageLocation;
        this.depositedAt = Objects.requireNonNull(depositedAt);
        this.pickedUpAt = pickedUpAt;
        this.depositedByActorId = depositedByActorId;
        this.pickedUpByActorId = pickedUpByActorId;
        this.recipientPhone = recipientPhone;
        this.notified = notified;
    }

    /** Registers a new package deposit at the hub. */
    public static HubPackageEntry deposit(UUID tenantId, UUID hubId, UUID packageId,
                                           String trackingCode, String storageLocation,
                                           UUID depositedByActorId, String recipientPhone) {
        return new HubPackageEntry(UUID.randomUUID(), tenantId, hubId, packageId, trackingCode,
                storageLocation, Instant.now(), null, depositedByActorId, null, recipientPhone, false);
    }

    public static HubPackageEntry rehydrate(UUID id, UUID tenantId, UUID hubId, UUID packageId,
                                             String trackingCode, String storageLocation,
                                             Instant depositedAt, Instant pickedUpAt,
                                             UUID depositedByActorId, UUID pickedUpByActorId,
                                             String recipientPhone, boolean notified) {
        return new HubPackageEntry(id, tenantId, hubId, packageId, trackingCode, storageLocation,
                depositedAt, pickedUpAt, depositedByActorId, pickedUpByActorId, recipientPhone, notified);
    }

    /**
     * Marks the package as picked up by a recipient or actor.
     */
    public HubPackageEntry markPickedUp(UUID pickedUpByActorId) {
        if (isPickedUp()) throw new IllegalStateException("Package already picked up: " + trackingCode);
        return new HubPackageEntry(id, tenantId, hubId, packageId, trackingCode, storageLocation,
                depositedAt, Instant.now(), depositedByActorId, pickedUpByActorId,
                recipientPhone, notified);
    }

    /** Marks the recipient as notified of package arrival. */
    public HubPackageEntry markNotified() {
        return new HubPackageEntry(id, tenantId, hubId, packageId, trackingCode, storageLocation,
                depositedAt, pickedUpAt, depositedByActorId, pickedUpByActorId, recipientPhone, true);
    }

    /**
     * Returns true if the package has been in the hub longer than the allowed max hours
     * without being picked up.
     *
     * @param maxHours maximum allowed storage duration in hours
     * @return true if overdue
     */
    public boolean isOverdue(long maxHours) {
        if (isPickedUp()) return false;
        Duration stored = Duration.between(depositedAt, Instant.now());
        return stored.toHours() >= maxHours;
    }

    public boolean isPickedUp() {
        return pickedUpAt != null;
    }

    public long storedHours() {
        Instant endTime = pickedUpAt != null ? pickedUpAt : Instant.now();
        return Duration.between(depositedAt, endTime).toHours();
    }

    public UUID id() { return id; }
    public UUID tenantId() { return tenantId; }
    public UUID hubId() { return hubId; }
    public UUID packageId() { return packageId; }
    public String trackingCode() { return trackingCode; }
    public String storageLocation() { return storageLocation; }
    public Instant depositedAt() { return depositedAt; }
    public Instant pickedUpAt() { return pickedUpAt; }
    public UUID depositedByActorId() { return depositedByActorId; }
    public UUID pickedUpByActorId() { return pickedUpByActorId; }
    public String recipientPhone() { return recipientPhone; }
    public boolean isNotified() { return notified; }
}
