package com.yowyob.tiibntick.core.sync.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Objects;

public record SyncToken(
        String value,
        String userId,
        String tenantId,
        String deviceId,
        LocalDateTime lastSyncAt
) {

    private static final Duration DEFAULT_STALE_DURATION = Duration.ofHours(24);

    @JsonCreator
    public static SyncToken fromJson(
            @JsonProperty("value") String value,
            @JsonProperty("userId") String userId,
            @JsonProperty("tenantId") String tenantId,
            @JsonProperty("deviceId") String deviceId,
            @JsonProperty("lastSyncAt") LocalDateTime lastSyncAt) {
        return new SyncToken(value, userId, tenantId, deviceId, lastSyncAt);
    }

    public static SyncToken initial(String userId, String tenantId, String deviceId) {
        Objects.requireNonNull(userId);
        Objects.requireNonNull(tenantId);
        LocalDateTime epoch = LocalDateTime.of(1970, 1, 1, 0, 0, 0);
        String val = encode(userId, tenantId, deviceId, epoch);
        return new SyncToken(val, userId, tenantId, deviceId, epoch);
    }

    public static SyncToken next(String userId, String tenantId, String deviceId, LocalDateTime timestamp) {
        Objects.requireNonNull(timestamp);
        String val = encode(userId, tenantId, deviceId, timestamp);
        return new SyncToken(val, userId, tenantId, deviceId, timestamp);
    }

    public boolean isStale() {
        return isStale(DEFAULT_STALE_DURATION);
    }

    public boolean isStale(Duration staleDuration) {
        return LocalDateTime.now().isAfter(lastSyncAt.plus(staleDuration));
    }

    public boolean isInitial() {
        return lastSyncAt.getYear() == 1970;
    }

    private static String encode(String userId, String tenantId, String deviceId, LocalDateTime ts) {
        String raw = userId + ":" + tenantId + ":" + (deviceId != null ? deviceId : "") + ":" + ts.toString();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes());
    }

    @Override
    public String toString() {
        return value;
    }
}
