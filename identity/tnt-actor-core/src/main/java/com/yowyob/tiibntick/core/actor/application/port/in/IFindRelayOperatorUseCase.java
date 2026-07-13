package com.yowyob.tiibntick.core.actor.application.port.in;

import com.yowyob.tiibntick.core.actor.domain.model.RelayOperatorProfile;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Inbound query port for {@link RelayOperatorProfile} — mirrors the existing
 * {@code IFindDelivererUseCase}/{@code IFindFreelancerUseCase} pattern, which
 * had no RelayOperator-side equivalent even though the outbound repository
 * already supported it.
 */
public interface IFindRelayOperatorUseCase {

    Mono<RelayOperatorProfile> findByActorId(UUID tenantId, UUID actorId);
}
