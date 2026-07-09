package com.yowyob.tiibntick.core.platformgateway.application.service;

import com.yowyob.tiibntick.core.platformgateway.application.port.out.IClientAuditLogRepository;
import com.yowyob.tiibntick.core.platformgateway.domain.model.AuditOutcome;
import com.yowyob.tiibntick.core.platformgateway.domain.model.ClientAuditLog;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.util.UUID;

/**
 * Records one platform-gateway request outcome (success or failure) — called by
 * {@code PlatformApiKeyWebFilter} on a fire-and-forget basis ({@code record(...)}
 * subscribes on a bounded-elastic scheduler and is never awaited by the response
 * chain) so the audit write can never add latency or a failure mode to the auth path
 * itself (see {@code docs/auth/platform-client-management-design.md} §2.3/§7).
 *
 * @author MANFOUO Braun
 */
public class PlatformClientAuditRecorder {

    private final IClientAuditLogRepository auditLogRepository;

    public PlatformClientAuditRecorder(IClientAuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /** Fire-and-forget — callers must NOT chain/await the returned handle on the response path. */
    public void record(UUID platformClientId, String clientIdAttempted, String endpoint, String httpMethod,
            AuditOutcome outcome, String ipAddress, String userAgent) {
        ClientAuditLog entry = new ClientAuditLog(
                UUID.randomUUID(), platformClientId, clientIdAttempted, endpoint, httpMethod,
                outcome, ipAddress, userAgent, Instant.now());
        Mono.defer(() -> auditLogRepository.save(entry))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(saved -> { }, error -> { /* audit failures must never surface to the caller */ });
    }
}
