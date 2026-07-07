package com.yowyob.tiibntick.core.realtime.adapter.out.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.realtime.application.port.out.IPresenceRepository;
import com.yowyob.tiibntick.core.realtime.domain.model.DeviceInfo;
import com.yowyob.tiibntick.core.realtime.domain.model.GeoCoordinates;
import com.yowyob.tiibntick.core.realtime.domain.model.PresenceRecord;
import com.yowyob.tiibntick.core.realtime.domain.model.enums.PresenceStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Redis-backed adapter implementing {@link IPresenceRepository}.
 *
 * <p>Key schema:</p>
 * <ul>
 *   <li>{@code tnt:presence:{tenantId}:{userId}} → JSON of presence data (with TTL)</li>
 *   <li>{@code tnt:presence:idx:{tenantId}} → Redis Set of all userIds in this tenant</li>
 * </ul>
 *
 * <p>The TTL on presence keys matches the configured presence TTL (default 30s).
 * If no heartbeat arrives in time, Redis expires the key automatically,
 * effectively marking the actor as offline.</p>
 *
 * @author MANFOUO Braun
 */
@Repository
public class RedisPresenceRepository implements IPresenceRepository {

    private static final Logger log = LoggerFactory.getLogger(RedisPresenceRepository.class);
    private static final String KEY_PREFIX = "tnt:presence:";
    private static final String INDEX_PREFIX = "tnt:presence:idx:";

    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${tnt.realtime.presence.ttl-seconds:30}")
    private int presenceTtlSeconds;

    public RedisPresenceRepository(@Qualifier("realtimeRedisTemplate") ReactiveStringRedisTemplate redisTemplate,
            @Qualifier("tntObjectMapper") ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> save(PresenceRecord record) {
        String key = buildKey(record.getTenantId(), record.getUserId());
        String indexKey = buildIndexKey(record.getTenantId());

        try {
            PresenceDto dto = PresenceDto.from(record);
            String json = objectMapper.writeValueAsString(dto);
            Duration ttl = Duration.ofSeconds(presenceTtlSeconds);

            return redisTemplate.opsForValue().set(key, json, ttl)
                    .then(redisTemplate.opsForSet().add(indexKey, record.getUserId()).then())
                    .doOnError(ex -> log.error("Failed to save presence for user {}: {}", record.getUserId(), ex.getMessage()));

        } catch (JsonProcessingException e) {
            return Mono.error(new RuntimeException("Failed to serialize presence record", e));
        }
    }

    @Override
    public Mono<PresenceRecord> findByUserAndTenant(String userId, String tenantId) {
        String key = buildKey(tenantId, userId);
        return redisTemplate.opsForValue().get(key)
                .flatMap(json -> {
                    try {
                        PresenceDto dto = objectMapper.readValue(json, PresenceDto.class);
                        return Mono.just(dto.toRecord());
                    } catch (JsonProcessingException e) {
                        log.warn("Failed to deserialize presence for user {}: {}", userId, e.getMessage());
                        return Mono.empty();
                    }
                });
    }

    @Override
    public Flux<PresenceRecord> findAllByTenant(String tenantId) {
        String indexKey = buildIndexKey(tenantId);
        return redisTemplate.opsForSet().members(indexKey)
                .flatMap(userId -> findByUserAndTenant(userId, tenantId));
    }

    @Override
    public Flux<PresenceRecord> findAllStale(Duration staleDuration) {
        // Stale detection is done per-record by checking lastSeenAt in the DTO
        // In practice, Redis TTL handles most of this automatically.
        // This method supplements for records that have not yet expired.
        //LocalDateTime threshold = LocalDateTime.now().minus(staleDuration);

        return redisTemplate.keys(KEY_PREFIX + "*")
                .flatMap(key -> redisTemplate.opsForValue().get(key))
                .flatMap(json -> {
                    try {
                        PresenceDto dto = objectMapper.readValue(json, PresenceDto.class);
                        PresenceRecord record = dto.toRecord();
                        if (record.isStale(staleDuration) && record.isOnline()) {
                            return Mono.just(record);
                        }
                        return Mono.empty();
                    } catch (JsonProcessingException e) {
                        return Mono.empty();
                    }
                });
    }

    @Override
    public Mono<Void> deleteByUserAndTenant(String userId, String tenantId) {
        String key = buildKey(tenantId, userId);
        String indexKey = buildIndexKey(tenantId);
        return redisTemplate.delete(key)
                .then(redisTemplate.opsForSet().remove(indexKey, userId).then());
    }

    // ─── Key builders ─────────────────────────────────────────────────────────

    private String buildKey(String tenantId, String userId) {
        return KEY_PREFIX + tenantId + ":" + userId;
    }

    private String buildIndexKey(String tenantId) {
        return INDEX_PREFIX + tenantId;
    }

    // ─── Internal DTO for Redis serialization ─────────────────────────────────

    record PresenceDto(
            String userId,
            String tenantId,
            String status,
            Double latitude,
            Double longitude,
            String activeMissionId,
            String lastSeenAt,
            String firstSeenAt,
            String devicePlatform,
            String appVersion,
            String pushToken
    ) {
        static PresenceDto from(PresenceRecord record) {
            GeoCoordinates coords = record.getCurrentCoordinates();
            DeviceInfo device = record.getDeviceInfo();
            return new PresenceDto(
                    record.getUserId(),
                    record.getTenantId(),
                    record.getStatus().name(),
                    coords != null ? coords.latitude() : null,
                    coords != null ? coords.longitude() : null,
                    record.getActiveMissionId(),
                    record.getLastSeenAt().toString(),
                    record.getFirstSeenAt().toString(),
                    device != null && device.platform() != null ? device.platform().name() : null,
                    device != null ? device.appVersion() : null,
                    device != null ? device.pushToken() : null
            );
        }

        PresenceRecord toRecord() {
            DeviceInfo deviceInfo = devicePlatform != null
                    ? DeviceInfo.of(
                            com.yowyob.tiibntick.core.realtime.domain.model.enums.DeviceType.valueOf(devicePlatform),
                            appVersion, "unknown", pushToken)
                    : null;

            PresenceRecord record = new PresenceRecord(userId, tenantId, deviceInfo);
            record.setStatus(PresenceStatus.valueOf(status));

            if (latitude != null && longitude != null) {
                record.updateLocation(GeoCoordinates.of(latitude, longitude));
            }
            if (activeMissionId != null) {
                record.assignMission(activeMissionId);
            }
            return record;
        }
    }
}
