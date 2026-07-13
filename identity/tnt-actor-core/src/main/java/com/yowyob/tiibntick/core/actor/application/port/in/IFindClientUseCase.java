package com.yowyob.tiibntick.core.actor.application.port.in;

import com.yowyob.tiibntick.core.actor.domain.model.ClientProfile;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Inbound query port for {@link ClientProfile} — mirrors the existing
 * {@code IFindDelivererUseCase}/{@code IFindFreelancerUseCase} pattern, which
 * had no Client-side equivalent even though the outbound repository already
 * supported it.
 */
public interface IFindClientUseCase {

    Mono<ClientProfile> findByActorId(UUID tenantId, UUID actorId);
}
