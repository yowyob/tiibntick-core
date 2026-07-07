package com.yowyob.tiibntick.core.realtime.application.port.out;

import com.yowyob.tiibntick.core.realtime.domain.model.WebSocketSession;
import reactor.core.publisher.Mono;

/**
 * Outbound port for persisting minimal session metadata in Redis.
 * Used for cross-instance session awareness (e.g. broadcasting to sessions
 * known to be on another instance).
 *
 * @author MANFOUO Braun
 */
public interface ISessionRepository {

    /**
     * Saves session metadata to Redis (sessionId → {userId, tenantId, instance}).
     *
     * @param session the domain session object
     * @return Mono completing after persistence
     */
    Mono<Void> save(WebSocketSession session);

    /**
     * Removes session metadata from Redis.
     *
     * @param sessionId the session identifier string
     * @return Mono completing after deletion
     */
    Mono<Void> deleteById(String sessionId);
}
