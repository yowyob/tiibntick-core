package com.yowyob.tiibntick.core.delivery.application.tracking;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Rate limit of the public tracking lookups per client IP (D-137): codes are guessable only by brute force, which
 * this window makes impractical. Fixed one-minute window, in memory (per instance).
 *
 * <p>Used by every public tracking entry point: delivery-core {@code GET /deliveries/track/{code}} and Link
 * {@code GET /platform/link/parcels/track/{code}}; the realtime SSE stream ({@code /realtime/sse/tracking/{code}})
 * cannot reach this bean (realtime-core does not depend on delivery-core) — see the L2 report.
 */
@Component
public class PublicTrackingRateLimiter {

    private final int limitPerMinute;
    private final Cache<String, AtomicInteger> windows = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(1))
            .maximumSize(100_000)
            .build();

    public PublicTrackingRateLimiter(
            @Value("${tnt.delivery.tracking.public-rate-limit-per-minute:30}") int limitPerMinute) {
        this.limitPerMinute = limitPerMinute;
    }

    /** @return whether one more lookup from {@code clientIp} is allowed in the current window */
    public boolean tryAcquire(String clientIp) {
        String key = clientIp == null || clientIp.isBlank() ? "unknown" : clientIp;
        return windows.get(key, k -> new AtomicInteger()).incrementAndGet() <= limitPerMinute;
    }

    public int limitPerMinute() {
        return limitPerMinute;
    }

    /** First address of {@code X-Forwarded-For}, else the remote address. */
    public static String clientIp(String forwardedFor, String remoteAddress) {
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return remoteAddress;
    }
}
