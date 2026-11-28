package com.yowyob.tiibntick.core.gofreelancer.application.port.in;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.relaypoint.RelayPointStatus;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.response.RelayPointDetailsResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface AdminRelayPointUseCase {

    Flux<RelayPointDetailsResponse> getPendingRelayPoints();

    Flux<RelayPointDetailsResponse> getAllRelayPoints(RelayPointStatus status);

    Mono<RelayPointDetailsResponse> getRelayPointDetails(UUID id);

    Mono<Void> validateRelayPoint(UUID id, boolean approved, String reason, String loginUrl);

    Mono<Void> suspendRelayPoint(UUID id, String loginUrl);

    /**
     * Revokes the relay point for good (decision D-129): delegates to the core ({@code IRevokeRelayHubUseCase}) when
     * the relay point has a core hub, otherwise revokes it locally with the same effects.
     *
     * @param reason       mandatory
     * @param adminActorId the administrator
     */
    Mono<RelayPointRevocation> revokeRelayPoint(UUID id, String reason, UUID adminActorId, String loginUrl);

    Mono<Void> activateRelayPoint(UUID id, String loginUrl);

    /**
     * The generic status change of a relay point ({@code PATCH .../relay-point-profiles/{id}/status}) routed through
     * the approval machine (D-138, D-139): APPROVED approves a PENDING relay point or reactivates a SUSPENDED one,
     * REJECTED rejects a PENDING one, SUSPENDED suspends an APPROVED one. PENDING is never a target (409); REVOKED
     * needs a reason and the {@code relay:revoke} permission: refused here (400), see {@link #revokeRelayPoint}.
     */
    Mono<Void> changeStatus(UUID id, RelayPointStatus target, String reason, String loginUrl);
}
