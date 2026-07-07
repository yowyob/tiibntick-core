package com.yowyob.tiibntick.core.inventory.domain.event;
import java.time.Instant;
import java.util.UUID;
public record PackageDepositedEvent(UUID hubPackageEntryId, UUID hubId, UUID packageId, String trackingCode, UUID tenantId, Instant occurredAt) {
    public static PackageDepositedEvent of(UUID entryId, UUID hubId, UUID packageId, String trackingCode, UUID tenantId) {
        return new PackageDepositedEvent(entryId, hubId, packageId, trackingCode, tenantId, Instant.now());
    }
}
