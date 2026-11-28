package com.yowyob.tiibntick.core.inventory.application.port.in;
import java.math.BigDecimal;
import java.util.UUID;
public record DepositHubPackageCommand(UUID tenantId, UUID hubId, UUID packageId, String trackingCode, String storageLocation, UUID depositedByActorId, String recipientPhone,
                                      UUID ownerActorId, BigDecimal storageFee, String feeCurrency) {

    /** A deposit without owner nor fee. */
    public DepositHubPackageCommand(UUID tenantId, UUID hubId, UUID packageId, String trackingCode,
                                    String storageLocation, UUID depositedByActorId, String recipientPhone) {
        this(tenantId, hubId, packageId, trackingCode, storageLocation, depositedByActorId, recipientPhone,
                null, null, null);
    }
}
