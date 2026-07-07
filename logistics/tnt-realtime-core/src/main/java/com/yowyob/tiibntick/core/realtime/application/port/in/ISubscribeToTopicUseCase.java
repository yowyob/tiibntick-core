package com.yowyob.tiibntick.core.realtime.application.port.in;

import com.yowyob.tiibntick.core.realtime.domain.model.SessionId;
import reactor.core.publisher.Mono;

/**
 * Use case for subscribing a WebSocket session to a broadcast topic.
 *
 * @author MANFOUO Braun
 */
public interface ISubscribeToTopicUseCase {

    /**
     * Subscribes a session to a broadcast topic so it receives future broadcasts.
     *
     * @param sessionId the subscribing session
     * @param topic     the STOMP topic path (e.g. /topic/delivery/MISSION-123)
     * @return Mono completing when the subscription is registered
     */
    Mono<Void> subscribeToTopic(SessionId sessionId, String topic);
}
