package com.yowyob.tiibntick.core.realtime.application.port.out;

import com.yowyob.tiibntick.core.realtime.domain.model.BroadcastTopic;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Outbound port for emitting Server-Sent Events to lightweight HTTP clients.
 *
 * @author MANFOUO Braun
 */
public interface ISseEmitter {

    /**
     * Creates a reactive stream that emits SSE messages for the given topic.
     * The stream stays open until the client disconnects.
     *
     * @param topic the topic to subscribe to
     * @return Flux of JSON-serialized event strings
     */
    Flux<String> subscribe(BroadcastTopic topic);

    /**
     * Emits a payload to SSE subscribers of a specific topic.
     *
     * @param topic   the target topic
     * @param payload the event payload (will be serialized to JSON)
     * @return Mono completing when emitted
     */
    Mono<Void> emit(BroadcastTopic topic, Object payload);
}
