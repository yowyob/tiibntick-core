package com.yowyob.tiibntick.core.platformgateway.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * The result of issuing (or rotating) an {@link ApiKey} — the ONLY moment the plaintext
 * secret is ever available. Returned once in the admin API response body; never
 * persisted, never retrievable again ("show-once", same UX as Stripe/GitHub PATs).
 *
 * @author MANFOUO Braun
 */
public record IssuedApiKey(
        UUID keyId,
        String prefix,
        String plaintextSecret,
        Instant expiresAt
) {
}
