package com.yowyob.tiibntick.core.inventory.application.port.in;
import java.util.UUID;
public record DepositHubPackageCommand(UUID tenantId, UUID hubId, UUID packageId, String trackingCode, String storageLocation, UUID depositedByActorId, String recipientPhone) {}
