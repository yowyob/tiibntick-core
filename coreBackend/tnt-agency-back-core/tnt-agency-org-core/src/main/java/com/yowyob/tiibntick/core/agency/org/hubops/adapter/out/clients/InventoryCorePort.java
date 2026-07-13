package com.yowyob.tiibntick.core.agency.org.hubops.adapter.out.clients;

import reactor.core.publisher.Mono;

import java.util.UUID;

public interface InventoryCorePort {

    Mono<HubPackageView> depositPackage(DepositPackageRequest request);

    Mono<Void> pickupPackage(PickupPackageRequest request);

    /** Returns occupancy rate between 0.0 and 1.0 for a platform hub. */
    Mono<Double> getOccupancyRate(UUID tenantId, UUID coreHubId);

    record DepositPackageRequest(
            UUID tenantId,
            UUID coreHubId,
            UUID packageId,
            String trackingCode,
            String storageLocation,
            UUID depositedByActorId,
            String recipientPhone) {}

    record PickupPackageRequest(String trackingCode, UUID pickedUpByActorId) {}

    record HubPackageView(
            UUID id,
            UUID tenantId,
            UUID hubId,
            UUID packageId,
            String trackingCode,
            String status) {}
}
