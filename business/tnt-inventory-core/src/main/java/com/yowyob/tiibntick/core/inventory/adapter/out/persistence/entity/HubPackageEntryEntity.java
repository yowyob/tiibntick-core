package com.yowyob.tiibntick.core.inventory.adapter.out.persistence.entity;

import com.yowyob.tiibntick.core.inventory.domain.model.HubPackageEntry;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;
import java.time.Instant;
import java.util.UUID;

@Table("tnt_hub_package_entries")
public class HubPackageEntryEntity implements Persistable<UUID> {
    @Id private UUID id;

    @Transient
    private boolean isNew;
    private UUID tenantId;
    private UUID hubId;
    private UUID packageId;
    private String trackingCode;
    private String storageLocation;
    private Instant depositedAt;
    private Instant pickedUpAt;
    private UUID depositedByActorId;
    private UUID pickedUpByActorId;
    private String recipientPhone;
    private boolean notified;

    public static HubPackageEntryEntity fromDomain(HubPackageEntry h) {
        HubPackageEntryEntity e = new HubPackageEntryEntity();
        e.id = h.id(); e.tenantId = h.tenantId(); e.hubId = h.hubId();
        e.packageId = h.packageId(); e.trackingCode = h.trackingCode();
        e.storageLocation = h.storageLocation(); e.depositedAt = h.depositedAt();
        e.pickedUpAt = h.pickedUpAt(); e.depositedByActorId = h.depositedByActorId();
        e.pickedUpByActorId = h.pickedUpByActorId(); e.recipientPhone = h.recipientPhone();
        e.notified = h.isNotified();
        return e;
    }

    public HubPackageEntry toDomain() {
        return HubPackageEntry.rehydrate(id, tenantId, hubId, packageId, trackingCode,
                storageLocation, depositedAt, pickedUpAt, depositedByActorId,
                pickedUpByActorId, recipientPhone, notified);
    }

    @Override public boolean isNew() { return isNew; }
    public void setNew(boolean isNew) { this.isNew = isNew; }

    public UUID getId() { return id; } public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; } public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getHubId() { return hubId; } public void setHubId(UUID hubId) { this.hubId = hubId; }
    public UUID getPackageId() { return packageId; } public void setPackageId(UUID packageId) { this.packageId = packageId; }
    public String getTrackingCode() { return trackingCode; } public void setTrackingCode(String trackingCode) { this.trackingCode = trackingCode; }
    public String getStorageLocation() { return storageLocation; } public void setStorageLocation(String storageLocation) { this.storageLocation = storageLocation; }
    public Instant getDepositedAt() { return depositedAt; } public void setDepositedAt(Instant depositedAt) { this.depositedAt = depositedAt; }
    public Instant getPickedUpAt() { return pickedUpAt; } public void setPickedUpAt(Instant pickedUpAt) { this.pickedUpAt = pickedUpAt; }
    public UUID getDepositedByActorId() { return depositedByActorId; } public void setDepositedByActorId(UUID depositedByActorId) { this.depositedByActorId = depositedByActorId; }
    public UUID getPickedUpByActorId() { return pickedUpByActorId; } public void setPickedUpByActorId(UUID pickedUpByActorId) { this.pickedUpByActorId = pickedUpByActorId; }
    public String getRecipientPhone() { return recipientPhone; } public void setRecipientPhone(String recipientPhone) { this.recipientPhone = recipientPhone; }
    public boolean isNotified() { return notified; } public void setNotified(boolean notified) { this.notified = notified; }
}
