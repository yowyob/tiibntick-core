package com.yowyob.tiibntick.core.geo.application.port.out;

import com.yowyob.tiibntick.core.geo.domain.model.HubCustodyItem;
import reactor.core.publisher.Flux;

import java.util.UUID;

/**
 * Outbound port: the parcels currently held by a hub. Implemented by the module that owns the hub inventory
 * (tnt-inventory-core, a higher layer, which depends down on geo — layering rule of the repository).
 */
public interface IRelayHubCustodyPort {

    /** Parcels deposited at the hub and not picked up yet. */
    Flux<HubCustodyItem> parcelsInCustody(UUID hubId);
}
