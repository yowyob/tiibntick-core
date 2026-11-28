package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.geo.application.port.out.IRelayHubApprovalListener;
import com.yowyob.tiibntick.core.geo.domain.model.HubApprovalStatus;
import com.yowyob.tiibntick.core.geo.domain.model.RelayHubApprovalChange;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.AdminRelayPointUseCase;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpRelayPointRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * The Go relay point copies the core hub's approval (D-138) also when the administrator used a core route (D-141):
 * told by the core after a real change, it runs the same Go flow as the Go admin routes (local status, relay operator
 * profile, KYC on approval, notification of the operator); the core step of that flow is then a no-op.
 *
 * <p>A change made through a Go route carries {@link #FROM_GO_ROUTE} in the Reactor context: the Go route aligns the
 * relay point itself, so this listener does nothing (no double notification). A hub that backs no Go relay point
 * (another product's hub) is ignored.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GofpRelayApprovalApplier implements IRelayHubApprovalListener {

    /** Reactor context key set by the Go admin flow around its call to the core approval. */
    public static final String FROM_GO_ROUTE = "gofp.relay.approval.fromGoRoute";

    private final GofpRelayPointRepository relayPoints;
    private final AdminRelayPointUseCase adminRelayPoints;

    @Override
    public Mono<Void> onApprovalChanged(RelayHubApprovalChange change) {
        return Mono.deferContextual(ctx -> {
            if (ctx.hasKey(FROM_GO_ROUTE)) {
                return Mono.<Void>empty();
            }
            return relayPoints.findByCoreRelayPointId(change.hub().id())
                    .flatMap(rp -> {
                        log.info("Relay point {} copies the core approval of hub {}: {} -> {}", rp.getId(),
                                change.hub().id(), change.previous(), change.hub().approvalStatus());
                        return switch (change.hub().approvalStatus()) {
                            case APPROVED -> change.previous() == HubApprovalStatus.SUSPENDED
                                    ? adminRelayPoints.activateRelayPoint(rp.getId(), null)
                                    : adminRelayPoints.validateRelayPoint(rp.getId(), true, null, null);
                            case REJECTED -> adminRelayPoints.validateRelayPoint(rp.getId(), false, change.reason(), null);
                            case SUSPENDED -> adminRelayPoints.suspendRelayPoint(rp.getId(), null);
                            // PENDING is never a target; REVOKED is handled by GofpRelayRevocationApplier
                            case PENDING, REVOKED -> Mono.<Void>empty();
                        };
                    })
                    .then();
        });
    }
}
