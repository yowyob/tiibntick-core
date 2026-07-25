package com.yowyob.tiibntick.core.linkback.adapter.out.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.linkback.application.port.out.ILinkPositionCache;
import com.yowyob.tiibntick.core.linkback.domain.model.LinkPosition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;

/**
 * Redis-backed latest-position cache (Chantier G, Audit n5 P-17). Key schema mirrors
 * {@code tnt-realtime-core}'s {@code RedisPresenceRepository} convention
 * ({@code tnt:presence:{tenantId}:{userId}}) but without its index-Set/{@code keys()}-scan
 * addition, whose mixed value/index key prefix causes a known WRONGTYPE bug
 * (docs/audits/remediation/phase-0-critical.md) — lookups here are always a direct
 * {@code (tenantId, missionId)} key, no scan ever needed.
 *
 * @author MANFOUO Braun
 */
@Slf4j
@Repository
public class LinkPositionRedisRepository implements ILinkPositionCache {

    private static final String KEY_PREFIX = "tnt:link:position:";

    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final int ttlSeconds;

    public LinkPositionRedisRepository(
            @Qualifier("reactiveStringRedisTemplate") ReactiveStringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${tnt.link-back.position.ttl-seconds:600}") int ttlSeconds) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.ttlSeconds = ttlSeconds;
    }

    @Override
    public Mono<Void> saveIfNewer(java.util.UUID tenantId, LinkPosition position) {
        String key = buildKey(tenantId, position.missionId());
        return findLatest(tenantId, position.missionId())
                .flatMap(existing -> existing.occurredAt().isAfter(position.occurredAt())
                        ? Mono.<Void>empty()
                        : write(key, position))
                .switchIfEmpty(Mono.defer(() -> write(key, position)));
    }

    @Override
    public Mono<LinkPosition> findLatest(java.util.UUID tenantId, String missionId) {
        String key = buildKey(tenantId, missionId);
        return redisTemplate.opsForValue().get(key)
                .flatMap(json -> {
                    try {
                        return Mono.just(objectMapper.readValue(json, PositionDto.class).toDomain());
                    } catch (JsonProcessingException e) {
                        log.warn("Failed to deserialize cached position for mission {}: {}", missionId, e.getMessage());
                        return Mono.empty();
                    }
                });
    }

    private Mono<Void> write(String key, LinkPosition position) {
        try {
            String json = objectMapper.writeValueAsString(PositionDto.from(position));
            return redisTemplate.opsForValue().set(key, json, Duration.ofSeconds(ttlSeconds)).then();
        } catch (JsonProcessingException e) {
            return Mono.error(new IllegalStateException("Failed to serialize position for mission " + position.missionId(), e));
        }
    }

    private String buildKey(java.util.UUID tenantId, String missionId) {
        return KEY_PREFIX + tenantId + ":" + missionId;
    }

    record PositionDto(String missionId, double latitude, double longitude, String occurredAt) {
        static PositionDto from(LinkPosition position) {
            return new PositionDto(position.missionId(), position.latitude(), position.longitude(),
                    position.occurredAt().toString());
        }

        LinkPosition toDomain() {
            return new LinkPosition(missionId, latitude, longitude, Instant.parse(occurredAt));
        }
    }
}
