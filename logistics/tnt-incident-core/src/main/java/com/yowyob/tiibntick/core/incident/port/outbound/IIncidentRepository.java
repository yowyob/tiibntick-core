package com.yowyob.tiibntick.core.incident.port.outbound;

import com.yowyob.tiibntick.core.incident.domain.enums.IncidentStatus;
import com.yowyob.tiibntick.core.incident.domain.enums.PlatformType;
import com.yowyob.tiibntick.core.incident.domain.model.Incident;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * Outbound port: persistence operations for the Incident aggregate.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */

public interface IIncidentRepository {
    Mono<Incident> save(Incident incident);
    Mono<Incident> findById(UUID id);
    Mono<Incident> findByReferenceCode(String referenceCode);
    Flux<Incident> findByMissionId(UUID missionId);
    Flux<Incident> findByAgencyIdAndStatus(UUID agencyId, IncidentStatus status);
    Flux<Incident> findByTenantIdAndStatus(UUID tenantId, IncidentStatus status);
    Flux<Incident> findByAgencyIdAndCreatedBetween(UUID agencyId, Instant from, Instant to);
    /** For self-service callers with no agency (GO/FREELANCER platforms) — "my incidents" scoped by reporter, not by agency. */
    Flux<Incident> findByReportedByActorIdAndCreatedBetween(UUID reportedByActorId, UUID tenantId, Instant from, Instant to);
    Flux<Incident> findActiveByPlatform(PlatformType platform, UUID tenantId);
    Flux<Incident> findByStatusIn(Iterable<IncidentStatus> statuses, UUID tenantId);
    Flux<Incident> findEscalatedIncidents(UUID tenantId);
    Mono<Long> countActiveByAgency(UUID agencyId);

    /** Lot L3 (QO-14): PENDING_CONFIRMATION incidents proposed before {@code before}, all tenants. */
    Flux<Incident> findConfirmationExpired(Instant before, int limit);

    /** Lot L3 (I15): AWAITING_HANDOVER incidents, all tenants. */
    Flux<Incident> findAwaitingHandover(int limit);
}
