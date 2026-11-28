package com.yowyob.tiibntick.core.inventory.adapter.out.geo;

import com.yowyob.tiibntick.core.geo.application.port.out.IRelayHubCustodyPort;
import com.yowyob.tiibntick.core.geo.domain.model.HubCustodyItem;
import com.yowyob.tiibntick.core.inventory.application.port.out.HubPackageEntryRepository;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.UUID;

/**
 * Implements geo's {@link IRelayHubCustodyPort}: the parcels a relay hub still holds are its inventory entries
 * not picked up. Used when an administrator revokes the hub (D-129) to list the parcels that must leave it.
 */
@Component
public class HubCustodyAdapter implements IRelayHubCustodyPort {

    private final HubPackageEntryRepository entries;

    public HubCustodyAdapter(HubPackageEntryRepository entries) {
        this.entries = entries;
    }

    @Override
    public Flux<HubCustodyItem> parcelsInCustody(UUID hubId) {
        return entries.findActiveByHub(hubId)
                .map(e -> new HubCustodyItem(e.id(), e.packageId(), e.trackingCode(), e.ownerActorId(),
                        e.depositedAt()));
    }
}
