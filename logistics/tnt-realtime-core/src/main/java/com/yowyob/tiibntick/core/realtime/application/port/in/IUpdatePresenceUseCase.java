package com.yowyob.tiibntick.core.realtime.application.port.in;

import com.yowyob.tiibntick.core.realtime.domain.model.enums.PresenceStatus;
import reactor.core.publisher.Mono;

/**
 * Use case for updating an actor's presence status.
 *
 * @author MANFOUO Braun
 */
public interface IUpdatePresenceUseCase {

    /**
     * Updates the presence status of a specific actor.
     *
     * @param userId   the actor's user identifier
     * @param tenantId the tenant context
     * @param status   the new presence status
     * @return Mono completing after persistence and broadcast
     */
    Mono<Void> updateStatus(String userId, String tenantId, PresenceStatus status);

    /**
     * Assigns an active mission to an actor.
     *
     * @param userId    the actor's user identifier
     * @param tenantId  the tenant context
     * @param deliveryId the delivery to assign
     * @return Mono completing after update
     */
    Mono<Void> assignDelivery(String userId, String tenantId, String deliveryId);

    /**
     * Clears the active mission from an actor's presence.
     *
     * @param userId   the actor's user identifier
     * @param tenantId the tenant context
     * @return Mono completing after update
     */
    Mono<Void> clearDelivery(String userId, String tenantId);
}
