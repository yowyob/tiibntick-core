package com.yowyob.tiibntick.core.platformgateway.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * A hashed API key belonging to exactly one {@link PlatformClient}. A client may have
 * more than one {@code ACTIVE} key at once (zero-downtime rotation, see
 * {@code docs/auth/platform-client-management-design.md} §2.2/§5.3).
 *
 * <p>Never carries the plaintext secret — only {@code keyPrefix} (non-secret, indexed,
 * for fast candidate lookup and display truncation) and {@code keyHash} (BCrypt). The
 * plaintext is generated once and returned once via {@link IssuedApiKey}, never stored.
 *
 * @author MANFOUO Braun
 */
public record ApiKey(
        UUID id,
        UUID platformClientId,
        String keyPrefix,
        String keyHash,
        ApiKeyStatus status,
        Instant expiresAt,
        Instant lastUsedAt,
        Instant createdAt,
        Instant revokedAt,
        String revokedBy,
        String revokedReason
) {
}
