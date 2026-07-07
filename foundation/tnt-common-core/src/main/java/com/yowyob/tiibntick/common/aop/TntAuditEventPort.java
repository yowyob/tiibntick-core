package com.yowyob.tiibntick.common.aop;

import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Port interface used by {@link TntAuditAspect} to record TiiBnTick audit entries.
 *
 * <p>This interface decouples the AOP aspect from the concrete audit implementation.
 * Consumers of {@code tnt-common-core} (such as {@code tnt-bootstrap}) provide
 * the implementation by wiring it to the Yowyob Kernel's audit service.
 *
 * <p>If no implementation is provided, the aspect falls back to logging only.
 *
 * Author: MANFOUO Braun
 * Created: 2025-10-01
 */
public interface TntAuditEventPort {

    /**
     * Records a TiiBnTick business audit event.
     *
     * @param tenantId      the tenant context (may be null for system-level events)
     * @param action        action code (e.g., "MISSION_CREATED")
     * @param aggregateType the DDD aggregate type (e.g., "Mission")
     * @param aggregateId   the aggregate primary key (may be null for creation events)
     * @param outcome       "SUCCESS" or "FAILURE"
     * @param errorMessage  error details (null on success)
     * @param correlationId HTTP request correlation ID (may be null)
     * @return Mono completing when the entry is recorded (fire-and-forget in production)
     */
    Mono<Void> record(UUID tenantId, String action, String aggregateType,
                      String aggregateId, String outcome, String errorMessage, String correlationId);
}
