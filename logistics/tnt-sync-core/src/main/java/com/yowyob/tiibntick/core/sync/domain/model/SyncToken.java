package com.yowyob.tiibntick.core.sync.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.yowyob.tiibntick.core.sync.domain.exception.SyncTokenExpiredException;

import java.nio.charset.StandardCharsets;
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

    /**
     * Decodes a Base64URL sync token produced by {@link #encode}.
     * Blank/null → initial token. Invalid payload or identity mismatch → {@link SyncTokenExpiredException}.
     */
    public static SyncToken parse(String encoded, String userId, String tenantId, String deviceId) {
        if (encoded == null || encoded.isBlank()) {
            return initial(userId, tenantId, deviceId);
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
            String[] parts = raw.split(":", 4);
            if (parts.length != 4) {
                throw new SyncTokenExpiredException(encoded);
            }
            String tokenUserId = parts[0];
            String tokenTenantId = parts[1];
            String tokenDeviceId = parts[2];
            LocalDateTime lastSyncAt = LocalDateTime.parse(parts[3]);

            if (!tokenUserId.equals(userId) || !tokenTenantId.equals(tenantId)) {
                throw new SyncTokenExpiredException(encoded);
            }

            String resolvedDevice = deviceId != null && !deviceId.isBlank() ? deviceId : tokenDeviceId;
            return new SyncToken(encoded, userId, tenantId, resolvedDevice, lastSyncAt);
        } catch (SyncTokenExpiredException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new SyncTokenExpiredException(encoded);
        }
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
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String toString() {
        return value;
    }
}
