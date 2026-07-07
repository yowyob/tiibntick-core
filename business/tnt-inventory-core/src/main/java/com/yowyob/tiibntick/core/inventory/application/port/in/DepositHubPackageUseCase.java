package com.yowyob.tiibntick.core.inventory.application.port.in;
import com.yowyob.tiibntick.core.inventory.domain.model.HubPackageEntry;
import reactor.core.publisher.Mono;
public interface DepositHubPackageUseCase {
    Mono<HubPackageEntry> depositPackage(DepositHubPackageCommand command);
}
