package com.yowyob.tiibntick.core.inventory.application.port.in;
import reactor.core.publisher.Mono;
import java.util.UUID;
public interface PickupHubPackageUseCase {
    Mono<Void> pickupPackage(String trackingCode, UUID pickedUpByActorId);
}
