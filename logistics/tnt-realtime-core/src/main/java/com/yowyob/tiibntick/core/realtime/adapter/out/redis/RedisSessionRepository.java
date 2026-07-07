package com.yowyob.tiibntick.core.realtime.adapter.out.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.realtime.application.port.out.ISessionRepository;
import com.yowyob.tiibntick.core.realtime.domain.model.WebSocketSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Redis-backed adapter implementing {@link ISessionRepository}.
 *
 * <p>Stores lightweight session metadata (userId, tenantId, instanceId) in Redis
 * for cross-instance session awareness. Key schema:
 * {@code tnt:session:{sessionId}} → JSON of session metadata.</p>
 *
 * @author MANFOUO Braun
 */
@Repository
public class RedisSessionRepository implements ISessionRepository {

    private static final Logger log = LoggerFactory.getLogger(RedisSessionRepository.class);
    private static final String KEY_PREFIX = "tnt:session:";

    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${tnt.realtime.session.ttl-seconds:3600}")
    private int sessionTtlSeconds;

    @Value("${tnt.realtime.instance.id:local}")
    private String instanceId;

    public RedisSessionRepository(@Qualifier("realtimeRedisTemplate") ReactiveStringRedisTemplate redisTemplate,
            @Qualifier("tntObjectMapper") ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> save(WebSocketSession session) {
        String key = KEY_PREFIX + session.getId().value();
        try {
            String json = objectMapper.writeValueAsString(new SessionMeta(
                    session.getId().value(), session.getUserId(),
                    session.getTenantId(), instanceId));
            return redisTemplate.opsForValue().set(key, json, Duration.ofSeconds(sessionTtlSeconds))
                    .then()
                    .doOnError(ex -> log.error("Failed to save session metadata {}: {}", session.getId(), ex.getMessage()));
        } catch (JsonProcessingException e) {
            return Mono.error(new RuntimeException("Failed to serialize session metadata", e));
        }
    }

    @Override
    public Mono<Void> deleteById(String sessionId) {
        return redisTemplate.delete(KEY_PREFIX + sessionId).then();
    }

    record SessionMeta(String sessionId, String userId, String tenantId, String instanceId) {}
}
