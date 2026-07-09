package com.yowyob.tiibntick.core.platformgateway.adapter.in.web.admin.dto;

import com.yowyob.tiibntick.core.platformgateway.domain.model.AuditOutcome;
import com.yowyob.tiibntick.core.platformgateway.domain.model.ClientAuditLog;

import java.time.Instant;
import java.util.UUID;

/**
 * Response body for a single audit-log entry.
 *
 * @author MANFOUO Braun
 */
public record ClientAuditLogResponse(
        UUID id,
        String clientIdAttempted,
        String endpoint,
        String httpMethod,
        AuditOutcome outcome,
        String ipAddress,
        String userAgent,
        Instant occurredAt
) {
    public static ClientAuditLogResponse from(ClientAuditLog log) {
        return new ClientAuditLogResponse(
                log.id(), log.clientIdAttempted(), log.endpoint(), log.httpMethod(),
                log.outcome(), log.ipAddress(), log.userAgent(), log.occurredAt());
    }
}
