package com.yowyob.tiibntick.core.geo.application.service;

import com.yowyob.tiibntick.core.geo.application.port.in.ICheckHubAcceptsDepositsUseCase;
import com.yowyob.tiibntick.core.geo.application.port.in.IManageHubApprovalUseCase;
import com.yowyob.tiibntick.core.geo.application.port.out.IRelayHubApprovalListener;
import com.yowyob.tiibntick.core.geo.application.port.out.IRelayHubRepository;
import com.yowyob.tiibntick.core.geo.domain.exception.GeoNotFoundException;
import com.yowyob.tiibntick.core.geo.domain.exception.RelayHubStateException;
import com.yowyob.tiibntick.core.geo.domain.model.HubApprovalStatus;
import com.yowyob.tiibntick.core.geo.domain.model.RelayHub;
import com.yowyob.tiibntick.core.geo.domain.model.RelayHubApprovalChange;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Approval of relay hubs by an administrator (D-138, D-139) and the deposit guard of D-140.
 *
 * <p>The hub is changed and saved first; then every {@link IRelayHubApprovalListener} is told (Go-Freelancer copies
 * the approval on its relay point and notifies the operator when the change came from a core route, D-141). A
 * listener failure is logged and never undoes the change. Asking for the current status again is a no-op: nothing
 * saved, nobody told, the hub returned as it is.
 */
@Slf4j
@Service
public class RelayHubApprovalService implements IManageHubApprovalUseCase, ICheckHubAcceptsDepositsUseCase {

    private final IRelayHubRepository hubRepository;
    private final ObjectProvider<IRelayHubApprovalListener> listeners;

    public RelayHubApprovalService(IRelayHubRepository hubRepository,
                                   ObjectProvider<IRelayHubApprovalListener> listeners) {
        this.hubRepository = hubRepository;
        this.listeners = listeners;
    }

    @Override
    public Mono<RelayHub> approve(UUID tenantId, UUID hubId, UUID actorId) {
        return change(tenantId, hubId, HubApprovalStatus.APPROVED, RelayHub::approve, null, actorId);
    }

    @Override
    public Mono<RelayHub> reject(UUID tenantId, UUID hubId, String reason, UUID actorId) {
        return change(tenantId, hubId, HubApprovalStatus.REJECTED, RelayHub::reject, reason, actorId);
    }

    @Override
    public Mono<RelayHub> suspend(UUID tenantId, UUID hubId, String reason, UUID actorId) {
        return change(tenantId, hubId, HubApprovalStatus.SUSPENDED, RelayHub::suspend, reason, actorId);
    }

    @Override
    public Mono<RelayHub> reactivate(UUID tenantId, UUID hubId, UUID actorId) {
        return change(tenantId, hubId, HubApprovalStatus.APPROVED, RelayHub::reactivate, null, actorId);
    }

    @Override
    public Mono<Void> requireAcceptsDeposits(UUID hubId) {
        if (hubId == null) return Mono.empty();
        return hubRepository.findById(hubId, null)
                .flatMap(hub -> hub.acceptsDeposits() ? Mono.<Void>empty()
                        : Mono.<Void>error(new RelayHubStateException("Hub " + hubId + " is " + hub.approvalStatus()
                                + ": no new deposit (D-140)")));
    }

    private Mono<RelayHub> change(UUID tenantId, UUID hubId, HubApprovalStatus target, Consumer<RelayHub> transition,
                                  String reason, UUID actorId) {
        String trimmed = reason == null || reason.isBlank() ? null : reason.strip();
        return hubRepository.findById(hubId, tenantId)
                .switchIfEmpty(Mono.error(new GeoNotFoundException("RelayHub", String.valueOf(hubId))))
                .flatMap(hub -> {
                    HubApprovalStatus previous = hub.approvalStatus();
                    if (previous == target) {
                        log.info("Relay hub {} already {}: nothing to do", hubId, target);
                        return Mono.just(hub);
                    }
                    transition.accept(hub);
                    return hubRepository.save(hub)
                            .doOnNext(saved -> log.info("Relay hub {} approval {} -> {} by {}{}", hubId, previous,
                                    saved.approvalStatus(), actorId, trimmed == null ? "" : " — reason: " + trimmed))
                            .flatMap(saved -> tell(new RelayHubApprovalChange(saved, previous, trimmed, actorId))
                                    .thenReturn(saved));
                });
    }

    private Mono<Void> tell(RelayHubApprovalChange change) {
        return Flux.fromStream(listeners.orderedStream())
                .concatMap(listener -> listener.onApprovalChanged(change)
                        .onErrorResume(e -> {
                            log.error("Relay hub {} approval changed, but listener {} failed", change.hub().id(),
                                    listener.getClass().getSimpleName(), e);
                            return Mono.empty();
                        }))
                .then();
    }
}
