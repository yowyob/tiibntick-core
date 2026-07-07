package com.yowyob.tiibntick.core.inventory.application.port.out;
import com.yowyob.tiibntick.core.inventory.domain.model.HubPackageEntry;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.UUID;
public interface HubPackageEntryRepository {
    Mono<HubPackageEntry> save(HubPackageEntry entry);
    Mono<HubPackageEntry> findById(UUID id);
    Mono<HubPackageEntry> findByTrackingCode(String trackingCode);
    Flux<HubPackageEntry> findActiveByHub(UUID hubId);
    Flux<HubPackageEntry> findAllByHub(UUID hubId);
}
