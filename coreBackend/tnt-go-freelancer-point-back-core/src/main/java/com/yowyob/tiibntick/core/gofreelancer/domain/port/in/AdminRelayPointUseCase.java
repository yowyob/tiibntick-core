package com.yowyob.tiibntick.core.gofreelancer.domain.port.in;

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

    Mono<Void> revokeRelayPoint(UUID id, String loginUrl);

    Mono<Void> activateRelayPoint(UUID id, String loginUrl);
}
