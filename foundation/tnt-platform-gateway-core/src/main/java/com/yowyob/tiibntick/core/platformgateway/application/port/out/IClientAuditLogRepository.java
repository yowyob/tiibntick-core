package com.yowyob.tiibntick.core.platformgateway.application.port.out;

import com.yowyob.tiibntick.core.platformgateway.domain.model.AuditOutcome;
import com.yowyob.tiibntick.core.platformgateway.domain.model.ClientAuditLog;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * Secondary (outbound) port for {@link ClientAuditLog} persistence — Postgres-only
 * (decided 2026-07-08, no Kafka/Elasticsearch sink).
 *
 * @author MANFOUO Braun
 */
public interface IClientAuditLogRepository {

    Mono<ClientAuditLog> save(ClientAuditLog log);

    Flux<ClientAuditLog> findPage(UUID platformClientId, AuditOutcome outcome, Instant from, Instant to, int page, int size);

    Mono<Long> count(UUID platformClientId, AuditOutcome outcome, Instant from, Instant to);
}
