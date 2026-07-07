package com.yowyob.tiibntick.core.realtime.adapter.out.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.realtime.application.port.out.IGeofenceZoneRepository;
import com.yowyob.tiibntick.core.realtime.domain.model.GeoCoordinates;
import com.yowyob.tiibntick.core.realtime.domain.model.GeofenceZone;
import com.yowyob.tiibntick.core.realtime.domain.model.enums.GeofenceZoneType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Redis-backed adapter implementing {@link IGeofenceZoneRepository}.
 *
 * <p>Key schema:</p>
 * <ul>
 *   <li>{@code tnt:geofence:{tenantId}:{zoneId}} → JSON of zone data</li>
 *   <li>{@code tnt:geofence:idx:{tenantId}} → Redis Hash of zoneId → active flag</li>
 * </ul>
 *
 * @author MANFOUO Braun
 */
@Repository
public class RedisGeofenceZoneRepository implements IGeofenceZoneRepository {

    private static final Logger log = LoggerFactory.getLogger(RedisGeofenceZoneRepository.class);
    private static final String ZONE_KEY_PREFIX = "tnt:geofence:";
    private static final String INDEX_PREFIX = "tnt:geofence:idx:";

    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisGeofenceZoneRepository(@Qualifier("realtimeRedisTemplate") ReactiveStringRedisTemplate redisTemplate,
            @Qualifier("tntObjectMapper") ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<GeofenceZone> save(GeofenceZone zone) {
        String key = buildKey(zone.getTenantId(), zone.getId());
        String indexKey = buildIndexKey(zone.getTenantId());

        try {
            GeofenceZoneDto dto = GeofenceZoneDto.from(zone);
            String json = objectMapper.writeValueAsString(dto);

            return redisTemplate.opsForValue().set(key, json)
                    .then(redisTemplate.opsForSet().add(indexKey, zone.getId()).then())
                    .thenReturn(zone)
                    .doOnError(ex -> log.error("Failed to save geofence zone {}: {}", zone.getId(), ex.getMessage()));
        } catch (JsonProcessingException e) {
            return Mono.error(new RuntimeException("Failed to serialize geofence zone", e));
        }
    }

    @Override
    public Flux<GeofenceZone> findActiveByTenant(String tenantId) {
        String indexKey = buildIndexKey(tenantId);
        return redisTemplate.opsForSet().members(indexKey)
                .flatMap(zoneId -> {
                    String key = buildKey(tenantId, zoneId);
                    return redisTemplate.opsForValue().get(key)
                            .flatMap(json -> {
                                try {
                                    GeofenceZoneDto dto = objectMapper.readValue(json, GeofenceZoneDto.class);
                                    GeofenceZone zone = dto.toZone();
                                    return zone.isActive() ? Mono.just(zone) : Mono.empty();
                                } catch (JsonProcessingException e) {
                                    log.warn("Failed to deserialize geofence zone {}: {}", zoneId, e.getMessage());
                                    return Mono.empty();
                                }
                            });
                });
    }

    @Override
    public Mono<Void> deleteByIdAndTenant(String zoneId, String tenantId) {
        String key = buildKey(tenantId, zoneId);
        String indexKey = buildIndexKey(tenantId);
        return redisTemplate.delete(key)
                .then(redisTemplate.opsForSet().remove(indexKey, zoneId).then());
    }

    // ─── Key builders ─────────────────────────────────────────────────────────

    private String buildKey(String tenantId, String zoneId) {
        return ZONE_KEY_PREFIX + tenantId + ":" + zoneId;
    }

    private String buildIndexKey(String tenantId) {
        return INDEX_PREFIX + tenantId;
    }

    // ─── Internal DTO ─────────────────────────────────────────────────────────

    record GeofenceZoneDto(
            String id,
            String tenantId,
            String name,
            double centerLatitude,
            double centerLongitude,
            double radiusMeters,
            String type,
            String linkedEntityId,
            boolean active
    ) {
        static GeofenceZoneDto from(GeofenceZone zone) {
            return new GeofenceZoneDto(
                    zone.getId(), zone.getTenantId(), zone.getName(),
                    zone.getCenter().latitude(), zone.getCenter().longitude(),
                    zone.getRadiusMeters(), zone.getType().name(),
                    zone.getLinkedEntityId(), zone.isActive());
        }

        GeofenceZone toZone() {
            GeofenceZone zone = GeofenceZone.builder()
                    .id(id)
                    .tenantId(tenantId)
                    .name(name)
                    .center(GeoCoordinates.of(centerLatitude, centerLongitude))
                    .radiusMeters(radiusMeters)
                    .type(GeofenceZoneType.valueOf(type))
                    .linkedEntityId(linkedEntityId)
                    .build();
            if (!active) zone.deactivate();
            return zone;
        }
    }
}
