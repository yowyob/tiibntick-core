package com.yowyob.tiibntick.core.inventory.application.port.in;
import com.yowyob.tiibntick.core.inventory.domain.model.HubPackageEntry;
import reactor.core.publisher.Flux;
import java.util.UUID;
public interface FindOverduePackagesUseCase {
    Flux<HubPackageEntry> findOverduePackages(UUID hubId, long maxHours);
}
