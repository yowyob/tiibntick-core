package com.yowyob.tiibntick.core.inventory.application.port.in;

import com.yowyob.tiibntick.core.inventory.domain.model.HubPackageEntry;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Reading the packages deposited at relay hubs (active and picked up). */
public interface FindHubPackagesUseCase {

    /** The entry, or empty when unknown. */
    Mono<HubPackageEntry> findEntry(UUID entryId);

    /** Every entry of the hub, active or picked up. */
    Flux<HubPackageEntry> findEntriesOfHub(UUID hubId);

    /** Every entry whose owner (the person who collects the package) is the given actor. */
    Flux<HubPackageEntry> findEntriesOfOwner(UUID ownerActorId);
}
