package com.yowyob.tiibntick.core.platformgateway.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * A TiiBnTick platform backend (Agency, Go, Link, Market, Point Relais, ...) registered
 * to call the Core's platform gateway. One row per (platform, environment) pair — see
 * {@code docs/auth/platform-client-management-design.md} §2.1.
 *
 * <p>{@code clientId} is a public identifier (not secret) — the secret lives only in
 * an associated {@link ApiKey}'s hash. Admin/persistence-level aggregate; the lighter
 * {@link PlatformClientApplication} is the resolved principal used at request-auth time.
 *
 * @author MANFOUO Braun
 */
public record PlatformClient(
        UUID id,
        String clientId,
        String name,
        String platformCode,
        Environment environment,
        ClientStatus status,
        String description,
        String contactEmail,
        Instant createdAt,
        Instant updatedAt,
        String createdBy,
        String updatedBy
) {
}
