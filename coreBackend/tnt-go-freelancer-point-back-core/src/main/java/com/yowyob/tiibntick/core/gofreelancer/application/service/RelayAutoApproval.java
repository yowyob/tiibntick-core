package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpRelayPointRepository;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpRelayPoint;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.relaypoint.RelayPointStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Manual relay point approval is suspended until the admin platform exists
 * ({@code tnt.gofp.relay.auto-approve}, true by default). While it is on:
 * <ul>
 *   <li>a relay point saved by its owner while still PENDING goes through the staff approval path
 *       ({@link AdminRelayPointApplicationService#approveOnRegistration});</li>
 *   <li>relay points already PENDING are caught up once at startup — same path, so their geo hub is
 *       provisioned too, which a SQL changeset could not do.</li>
 * </ul>
 * REJECTED, SUSPENDED or REVOKED relay points are never touched: staff decisions stand. Ownership
 * guards are unchanged. Off: behaviour of before (PENDING until a staff approval).
 */
@Slf4j
@Service
public class RelayAutoApproval {

    private final AdminRelayPointApplicationService admin;
    private final GofpRelayPointRepository relayPoints;
    private final boolean enabled;

    public RelayAutoApproval(AdminRelayPointApplicationService admin, GofpRelayPointRepository relayPoints,
                             @Value("${tnt.gofp.relay.auto-approve:true}") boolean enabled) {
        this.admin = admin;
        this.relayPoints = relayPoints;
        this.enabled = enabled;
    }

    public boolean enabled() {
        return enabled;
    }

    /** After an owner's write: a relay point still PENDING is approved; anything else is returned as is. */
    public Mono<GofpRelayPoint> afterOwnerWrite(GofpRelayPoint saved) {
        if (!enabled || saved == null || saved.getStatus() != RelayPointStatus.PENDING) {
            return Mono.justOrEmpty(saved);
        }
        return admin.approveOnRegistration(saved)
                .onErrorResume(e -> {
                    log.warn("Auto-approval of relay point {} failed — left PENDING: {}", saved.getId(), e.getMessage());
                    return Mono.just(saved);
                });
    }

    /** Startup catch-up of the relay points registered while approval was manual. */
    @EventListener(ApplicationReadyEvent.class)
    public void catchUpPending() {
        if (!enabled) return;
        relayPoints.findAllByStatus(RelayPointStatus.PENDING)
                .concatMap(rp -> admin.approveOnRegistration(rp)
                        .doOnNext(ok -> log.info("Relay point {} auto-approved (catch-up)", ok.getId()))
                        .onErrorResume(e -> {
                            log.warn("Catch-up auto-approval of relay point {} failed: {}", rp.getId(), e.getMessage());
                            return Mono.empty();
                        }))
                .subscribe();
    }
}
