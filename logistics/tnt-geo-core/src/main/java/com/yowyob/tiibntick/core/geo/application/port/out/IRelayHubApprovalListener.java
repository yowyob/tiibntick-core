package com.yowyob.tiibntick.core.geo.application.port.out;

import com.yowyob.tiibntick.core.geo.domain.model.RelayHubApprovalChange;
import reactor.core.publisher.Mono;

/**
 * Outbound port implemented by product backends whose own records copy the core approval (Go-Freelancer relay
 * points, D-138): told after a real change of a hub's approval, whatever the route that made it (core route D-141 or
 * product route). A failure is logged by the caller and never undoes the change.
 */
public interface IRelayHubApprovalListener {

    Mono<Void> onApprovalChanged(RelayHubApprovalChange change);
}
