package com.yowyob.tiibntick.core.agency.org.hubops.domain;

import com.yowyob.tiibntick.core.agency.org.hubops.domain.vo.ParcelStatus;

import java.time.Instant;
import java.util.UUID;

public class HubParcelRecord {

    private final UUID id;
    private final UUID tenantId;
    private final UUID hubId;
    private final UUID packageId;
    private final UUID missionId;
    private final String trackingCode;
    private final Instant depositedAt;
    private final Instant withdrawalDeadline;
    private ParcelStatus status;
    private boolean identityVerified;
    private String withdrawnBy;
    private UUID coreHubPackageEntryId;
    private final Instant createdAt;
    private Instant updatedAt;
    private long version;

    public HubParcelRecord(UUID id, UUID tenantId, UUID hubId, UUID packageId, UUID missionId,
                           String trackingCode, Instant depositedAt, Instant withdrawalDeadline,
                           ParcelStatus status, boolean identityVerified, String withdrawnBy,
                           UUID coreHubPackageEntryId,
                           Instant createdAt, Instant updatedAt, long version) {
        this.id = id;
        this.tenantId = tenantId;
        this.hubId = hubId;
        this.packageId = packageId;
        this.missionId = missionId;
        this.trackingCode = trackingCode;
        this.depositedAt = depositedAt;
        this.withdrawalDeadline = withdrawalDeadline;
        this.status = status;
        this.identityVerified = identityVerified;
        this.withdrawnBy = withdrawnBy;
        this.coreHubPackageEntryId = coreHubPackageEntryId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
    }

    public static HubParcelRecord deposit(UUID id, UUID tenantId, UUID hubId,
                                          UUID packageId, UUID missionId,
                                          String trackingCode, int retentionHours, Instant now) {
        Instant deadline = now.plusSeconds((long) retentionHours * 3600L);
        return new HubParcelRecord(id, tenantId, hubId, packageId, missionId, trackingCode,
                now, deadline, ParcelStatus.DEPOSITED, false, null, null, now, now, 0L);
    }

    public void withdraw(String withdrawnBy, boolean identityVerified, Instant now) {
        if (status != ParcelStatus.DEPOSITED) {
            throw new IllegalStateException("Only a DEPOSITED parcel can be withdrawn");
        }
        this.status = ParcelStatus.WITHDRAWN;
        this.withdrawnBy = withdrawnBy;
        this.identityVerified = identityVerified;
        this.updatedAt = now;
    }

    public void linkCoreEntry(UUID coreEntryId, Instant now) {
        this.coreHubPackageEntryId = coreEntryId;
        this.updatedAt = now;
    }

    public void markExpired(Instant now) {
        if (status != ParcelStatus.DEPOSITED) {
            throw new IllegalStateException("Only a DEPOSITED parcel can expire");
        }
        this.status = ParcelStatus.EXPIRED;
        this.updatedAt = now;
    }

    public boolean isExpired(Instant now) {
        return status == ParcelStatus.DEPOSITED
                && withdrawalDeadline != null
                && withdrawalDeadline.isBefore(now);
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getHubId() { return hubId; }
    public UUID getPackageId() { return packageId; }
    public UUID getMissionId() { return missionId; }
    public String getTrackingCode() { return trackingCode; }
    public Instant getDepositedAt() { return depositedAt; }
    public Instant getWithdrawalDeadline() { return withdrawalDeadline; }
    public ParcelStatus getStatus() { return status; }
    public boolean isIdentityVerified() { return identityVerified; }
    public String getWithdrawnBy() { return withdrawnBy; }
    public UUID getCoreHubPackageEntryId() { return coreHubPackageEntryId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
