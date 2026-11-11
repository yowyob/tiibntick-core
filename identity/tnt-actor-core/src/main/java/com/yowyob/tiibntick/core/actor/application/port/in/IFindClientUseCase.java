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

    /** Finds a client by its profile id (the {@code id} returned as {@code clientId}). */
    Mono<ClientProfile> findById(UUID tenantId, UUID profileId);

    /**
     * The caller's own profile: the one of the current space if there is one, otherwise the one the account
     * created in another space. A profile belongs to the person, so signing in through a different space must
     * not make it vanish (and must not invite the person to create it again).
     */
    Mono<ClientProfile> findMine(UUID tenantId, UUID actorId);
}
