package com.yowyob.tiibntick.core.platformgateway.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * One recorded platform-gateway request, success or failure — see
 * {@code docs/auth/platform-client-management-design.md} §3/§7. Written asynchronously
 * (never on the critical auth path) to a Postgres-only table (decided 2026-07-08 — no
 * Kafka/Elasticsearch sink).
 *
 * @author MANFOUO Braun
 */
public record ClientAuditLog(
        UUID id,
        UUID platformClientId,
        String clientIdAttempted,
        String endpoint,
        String httpMethod,
        AuditOutcome outcome,
        String ipAddress,
        String userAgent,
        Instant occurredAt
) {
}
