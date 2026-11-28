package com.yowyob.tiibntick.core.geo.application.service;

import com.yowyob.tiibntick.core.geo.application.port.in.IRevokeRelayHubUseCase;
import com.yowyob.tiibntick.core.geo.application.port.out.IRelayHubCustodyPort;
import com.yowyob.tiibntick.core.geo.application.port.out.IRelayHubRepository;
import com.yowyob.tiibntick.core.geo.application.port.out.IRelayHubRevocationListener;
import com.yowyob.tiibntick.core.geo.domain.exception.GeoNotFoundException;
import com.yowyob.tiibntick.core.geo.domain.model.HubCustodyItem;
import com.yowyob.tiibntick.core.geo.domain.model.RelayHub;
import com.yowyob.tiibntick.core.geo.domain.model.RelayHubRevocation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Revocation of a relay hub by an administrator (maintainer decision D-129, 2026-11-26).
 *
 * <p>Order: the hub is revoked and saved first (the decision is definitive whatever happens next); then the
 * parcels still in custody are read from the inventory, traced (WARN log) and returned to the caller; then every
 * {@link IRelayHubRevocationListener} is told. A custody read or a listener failure is logged and never undoes the
 * revocation.
 *
 * <p>Not decided here (to wire with the parcel lifecycle, events workstream lot L2): the return or transfer of
 * the parcels in custody — the decision only says they are "returned or transferred".
 */
@Slf4j
@Service
public class RelayHubRevocationService implements IRevokeRelayHubUseCase {

    private final IRelayHubRepository hubRepository;
    private final ObjectProvider<IRelayHubCustodyPort> custodyPort;
    private final ObjectProvider<IRelayHubRevocationListener> listeners;

    public RelayHubRevocationService(IRelayHubRepository hubRepository,
                                     ObjectProvider<IRelayHubCustodyPort> custodyPort,
                                     ObjectProvider<IRelayHubRevocationListener> listeners) {
        this.hubRepository = hubRepository;
        this.custodyPort = custodyPort;
        this.listeners = listeners;
    }

    @Override
    public Mono<RelayHubRevocation> revoke(UUID tenantId, UUID hubId, String reason, UUID revokedByActorId) {
        return hubRepository.findById(hubId, tenantId)
                .switchIfEmpty(Mono.error(new GeoNotFoundException("RelayHub", String.valueOf(hubId))))
                .flatMap(hub -> {
                    hub.revoke(reason, revokedByActorId);
                    return hubRepository.save(hub);
                })
                .flatMap(saved -> custody(saved).map(items -> items.isEmpty()
                        ? new RelayHubRevocation(saved, List.of(), false)
                        : new RelayHubRevocation(saved, items.get(), true)))
                .doOnNext(this::trace)
                .flatMap(revocation -> Flux.fromStream(listeners.orderedStream())
                        .concatMap(listener -> listener.onRevoked(revocation)
                                .onErrorResume(e -> {
                                    log.error("Relay hub {} revoked, but listener {} failed", hubId,
                                            listener.getClass().getSimpleName(), e);
                                    return Mono.empty();
                                }))
                        .then(Mono.just(revocation)));
    }

    /** The parcels in custody, or empty when they cannot be read. */
    private Mono<Optional<List<HubCustodyItem>>> custody(RelayHub hub) {
        IRelayHubCustodyPort port = custodyPort.getIfAvailable();
        if (port == null) {
            log.warn("Relay hub {} revoked: no inventory adapter, parcels in custody unknown", hub.id());
            return Mono.just(Optional.empty());
        }
        return port.parcelsInCustody(hub.id()).collectList()
                .map(Optional::of)
                .onErrorResume(e -> {
                    log.error("Relay hub {} revoked: parcels in custody could not be read", hub.id(), e);
                    return Mono.just(Optional.empty());
                });
    }

    private void trace(RelayHubRevocation r) {
        RelayHub hub = r.hub();
        if (!r.custodyKnown()) {
            log.warn("Relay hub {} (tenant {}) REVOKED by {} — reason: {} — parcels in custody: UNKNOWN",
                    hub.id(), hub.tenantId(), hub.revokedByActorId(), hub.revocationReason());
            return;
        }
        log.warn("Relay hub {} (tenant {}) REVOKED by {} — reason: {} — {} parcel(s) in custody to return or transfer "
                        + "(parcel lifecycle not wired yet): {}",
                hub.id(), hub.tenantId(), hub.revokedByActorId(), hub.revocationReason(),
                r.parcelsInCustody().size(),
                r.parcelsInCustody().stream()
                        .map(i -> i.trackingCode() + "(entry " + i.entryId() + ", parcel " + i.packageId() + ")")
                        .collect(Collectors.joining(", ")));
    }
}
