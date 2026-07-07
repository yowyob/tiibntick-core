package com.yowyob.tiibntick.core.inventory.domain.event;
import java.time.Instant;
import java.util.UUID;
public record PackagePickedUpEvent(UUID hubPackageEntryId, UUID hubId, String trackingCode, UUID tenantId, Instant occurredAt) {
    public static PackagePickedUpEvent of(UUID entryId, UUID hubId, String trackingCode, UUID tenantId) {
        return new PackagePickedUpEvent(entryId, hubId, trackingCode, tenantId, Instant.now());
    }
}
