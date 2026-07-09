package com.yowyob.tiibntick.core.platformgateway.adapter.in.web.admin.dto;

import com.yowyob.tiibntick.core.platformgateway.domain.model.ApiKey;
import com.yowyob.tiibntick.core.platformgateway.domain.model.ApiKeyStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Response body listing an {@link ApiKey}'s metadata — prefix only, never the hash or
 * the plaintext secret.
 *
 * @author MANFOUO Braun
 */
public record ApiKeyResponse(
        UUID id,
        String prefix,
        ApiKeyStatus status,
        Instant expiresAt,
        Instant lastUsedAt,
        Instant createdAt,
        Instant revokedAt,
        String revokedReason
) {
    public static ApiKeyResponse from(ApiKey key) {
        return new ApiKeyResponse(
                key.id(), key.keyPrefix(), key.status(), key.expiresAt(), key.lastUsedAt(),
                key.createdAt(), key.revokedAt(), key.revokedReason());
    }
}
