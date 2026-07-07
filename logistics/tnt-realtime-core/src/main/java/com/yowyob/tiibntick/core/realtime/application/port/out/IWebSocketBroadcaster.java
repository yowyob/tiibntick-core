package com.yowyob.tiibntick.core.realtime.application.port.out;

import com.yowyob.tiibntick.core.realtime.domain.model.BroadcastTopic;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;

/**
 * Outbound port for broadcasting messages via WebSocket to topic subscribers.
 * Implementations must support multi-instance scaling via Redis pub-sub.
 *
 * @author MANFOUO Braun
 */
public interface IWebSocketBroadcaster {

    /**
     * Broadcasts a payload to all WebSocket sessions subscribed to the given topic.
     * The payload will be serialized to JSON before transmission.
     *
     * @param topic   the broadcast topic
     * @param payload the object to broadcast
     * @return Mono completing when the broadcast is dispatched
     */
    Mono<Void> broadcast(BroadcastTopic topic, Object payload);

    /**
     * Broadcasts a pre-serialized JSON string directly to topic subscribers.
     *
     * @param topic   the broadcast topic
     * @param json    the JSON string to broadcast
     * @return Mono completing when broadcast is dispatched
     */
    Mono<Void> broadcastRaw(BroadcastTopic topic, String json);

    /**
     * Broadcasts a payload to all WebSocket sessions subscribed to the given topic path (string).
     * Convenience method when you have the topic path as a String instead of a BroadcastTopic object.
     *
     * @param topicPath the STOMP topic path string (e.g., /topic/fleet/FRL-ORG-001)
     * @param payload   the object to broadcast
     * @return Mono completing when the broadcast is dispatched
     */
    Mono<Void> broadcastToTopic(String topicPath, Object payload);

    /**
     * Returns a live stream of messages broadcast to a given topic ().
     *
     * <p>Used by WatchSubDeliverersApplicationService to expose the fleet topic
     * as a reactive Flux for SSE or WebSocket streaming to the OWNER.
     *
     * @param topicPath the STOMP topic path (e.g., /topic/fleet/FRL-ORG-001)
     * @return hot Flux of messages published to this topic
     */
    default reactor.core.publisher.Flux<Object> subscribeToTopic(String topicPath) {
        // Default: empty — concrete Redis/Kafka adapter overrides
        return Flux.empty();
    }

}