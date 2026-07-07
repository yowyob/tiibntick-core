package com.yowyob.tiibntick.core.realtime.application.port.in;

import com.yowyob.tiibntick.core.realtime.domain.model.PresenceRecord;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Use case for querying actor presence information.
 *
 * @author MANFOUO Braun
 */
public interface IGetPresenceUseCase {

    /**
     * Returns the presence record for a specific actor.
     *
     * @param userId   the actor's user identifier
     * @param tenantId the tenant context
     * @return Mono with the presence record, or empty if offline
     */
    Mono<PresenceRecord> getPresence(String userId, String tenantId);

    /**
     * Returns all currently online actors within a tenant.
     *
     * @param tenantId the tenant context
     * @return Flux of online presence records
     */
    Flux<PresenceRecord> getOnlineActors(String tenantId);

    /**
     * Checks whether a specific actor is currently online.
     *
     * @param userId   the actor's identifier
     * @param tenantId the tenant context
     * @return Mono<Boolean> true if the actor is online
     */
    Mono<Boolean> isOnline(String userId, String tenantId);
}
