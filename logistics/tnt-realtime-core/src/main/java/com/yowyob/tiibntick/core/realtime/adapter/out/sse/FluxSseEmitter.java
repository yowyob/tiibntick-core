package com.yowyob.tiibntick.core.realtime.adapter.out.sse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.realtime.application.port.out.ISseEmitter;
import com.yowyob.tiibntick.core.realtime.domain.model.BroadcastTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.connection.ReactiveSubscription;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Outbound adapter implementing {@link ISseEmitter} using Redis pub-sub.
 *
 * <p>SSE streams are backed by Redis channel subscriptions. When a message
 * is published to a Redis channel, all SSE subscribers for that topic receive it.
 * This design supports horizontal scaling: SSE clients can connect to any instance.</p>
 *
 * @author MANFOUO Braun
 */
@Component
public class FluxSseEmitter implements ISseEmitter {

    private static final Logger log = LoggerFactory.getLogger(FluxSseEmitter.class);

    private final ReactiveRedisMessageListenerContainer listenerContainer;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public FluxSseEmitter(ReactiveRedisMessageListenerContainer listenerContainer,
                          @Qualifier("realtimeRedisTemplate") ReactiveStringRedisTemplate redisTemplate,
                          ObjectMapper objectMapper) {
        this.listenerContainer = listenerContainer;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Flux<String> subscribe(BroadcastTopic topic) {
        String redisChannel = topic.toRedisChannel();

        return listenerContainer
                .receive(ChannelTopic.of(redisChannel))
                .map(ReactiveSubscription.Message::getMessage)
                .map(wireMessage -> {
                    int separatorIdx = wireMessage.indexOf('|');
                    return separatorIdx >= 0 ? wireMessage.substring(separatorIdx + 1) : wireMessage;
                })
                .doOnSubscribe(s -> log.debug("SSE client subscribed to Redis channel: {}", redisChannel))
                .doOnCancel(() -> log.debug("SSE client disconnected from Redis channel: {}", redisChannel))
                .doOnError(ex -> log.warn("SSE stream error for channel {}: {}", redisChannel, ex.getMessage()))
                .onErrorResume(ex -> Flux.empty());
    }

    @Override
    public Mono<Void> emit(BroadcastTopic topic, Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            String wireMessage = topic.path() + "|" + json;
            return redisTemplate.convertAndSend(topic.toRedisChannel(), wireMessage)
                    .then();
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize SSE payload for topic {}: {}", topic, e.getMessage());
            return Mono.error(e);
        }
    }
}
