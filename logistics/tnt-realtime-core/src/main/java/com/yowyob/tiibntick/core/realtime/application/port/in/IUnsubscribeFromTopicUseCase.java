package com.yowyob.tiibntick.core.realtime.application.port.in;

import com.yowyob.tiibntick.core.realtime.domain.model.SessionId;
import reactor.core.publisher.Mono;

/**
 * Use case for unsubscribing a session from a broadcast topic.
 *
 * @author MANFOUO Braun
 */
public interface IUnsubscribeFromTopicUseCase {

    /**
     * Removes a session's subscription from a topic.
     *
     * @param sessionId the session to unsubscribe
     * @param topic     the STOMP topic path
     * @return Mono completing when the subscription is removed
     */
    Mono<Void> unsubscribeFromTopic(SessionId sessionId, String topic);
}
