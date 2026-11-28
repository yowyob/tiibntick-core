package com.yowyob.tiibntick.core.gofreelancer.adapter.out.inventory;

import com.yowyob.tiibntick.core.gofreelancer.application.port.out.RelayDepositRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.service.RelayDepositViews;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.RelayDeposit;
import com.yowyob.tiibntick.core.inventory.application.port.in.FindHubPackagesUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Relay deposits read from the inventory hub package entries. */
@Component
@RequiredArgsConstructor
public class InventoryRelayDepositReader implements RelayDepositRepository {

    private final FindHubPackagesUseCase hubPackages;

    @Override
    public Mono<RelayDeposit> findById(UUID id) {
        return hubPackages.findEntry(id).map(RelayDepositViews::of);
    }

    @Override
    public Flux<RelayDeposit> findByRelayPointId(UUID relayPointId) {
        return relayPointId == null ? Flux.empty()
                : hubPackages.findEntriesOfHub(relayPointId).map(RelayDepositViews::of);
    }

    @Override
    public Flux<RelayDeposit> findByClientId(UUID clientId) {
        return clientId == null ? Flux.empty()
                : hubPackages.findEntriesOfOwner(clientId).map(RelayDepositViews::of);
    }
}
