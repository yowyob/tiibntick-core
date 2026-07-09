package com.yowyob.tiibntick.core.platformgateway.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Historical record of one API-key rotation for a {@link PlatformClient}.
 *
 * @author MANFOUO Braun
 */
public record ApiKeyRotationRecord(
        UUID id,
        UUID platformClientId,
        UUID oldApiKeyId,
        UUID newApiKeyId,
        Instant rotatedAt,
        String rotatedBy,
        String reason
) {
}
