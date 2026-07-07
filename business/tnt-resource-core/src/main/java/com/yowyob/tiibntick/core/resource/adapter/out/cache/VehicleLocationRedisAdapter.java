package com.yowyob.tiibntick.core.resource.adapter.out.cache;

import com.yowyob.tiibntick.core.resource.application.port.out.VehicleLocationPort;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.UUID;

/**
 * Driven adapter: implements VehicleLocationPort using Redis for fast GPS caching.
 * Key format: "tnt:vehicle:location:{vehicleId}" → "lat,lng"
 * TTL: 1 hour (refreshed on each update).
 * Used by tnt-realtime-core for real-time tracking dashboards.
 *
 * @author MANFOUO Braun.
 */
@Component
public class VehicleLocationRedisAdapter implements VehicleLocationPort {

    private static final String KEY_PREFIX = "tnt:vehicle:location:";
    private static final Duration TTL = Duration.ofHours(1);

    private final ReactiveRedisTemplate<String, String> redisTemplate;

    public VehicleLocationRedisAdapter(@Qualifier("reactiveStringRedisTemplate") ReactiveRedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Mono<Void> updateLocation(UUID vehicleId, double latitude, double longitude) {
        String key = KEY_PREFIX + vehicleId;
        String value = latitude + "," + longitude;
        return redisTemplate.opsForValue()
                .set(key, value, TTL)
                .then();
    }

    @Override
    public Mono<double[]> getLocation(UUID vehicleId) {
        String key = KEY_PREFIX + vehicleId;
        return redisTemplate.opsForValue()
                .get(key)
                .mapNotNull(value -> {
                    String[] parts = value.split(",");
                    if (parts.length != 2) return null;
                    return new double[]{Double.parseDouble(parts[0]), Double.parseDouble(parts[1])};
                });
    }

    @Override
    public Mono<Void> evictLocation(UUID vehicleId) {
        return redisTemplate.delete(KEY_PREFIX + vehicleId).then();
    }
}
