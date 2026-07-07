package com.yowyob.tiibntick.core.incident.adapter.persistence.repository;
import com.yowyob.tiibntick.core.incident.adapter.persistence.entity.IncidentEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.time.Instant;
import java.util.UUID;
/**
 * Spring Data R2DBC repository for Incident entities with custom queries.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */

public interface IncidentR2dbcRepository extends ReactiveCrudRepository<IncidentEntity, UUID> {
    Mono<IncidentEntity> findByReferenceCode(String referenceCode);
    Flux<IncidentEntity> findByMissionId(UUID missionId);
    Flux<IncidentEntity> findByAgencyIdAndStatus(UUID agencyId, String status);
    Flux<IncidentEntity> findByTenantIdAndStatus(UUID tenantId, String status);
    @Query("SELECT * FROM tnt_incidents WHERE agency_id = :agencyId AND reported_at BETWEEN :from AND :to")
    Flux<IncidentEntity> findByAgencyIdAndReportedAtBetween(UUID agencyId, Instant from, Instant to);
    @Query("SELECT * FROM tnt_incidents WHERE tenant_id = :tenantId AND source_platform = :platform AND status NOT IN ('CLOSED','CANCELLED')")
    Flux<IncidentEntity> findActiveByPlatform(UUID tenantId, String platform);
    @Query("SELECT * FROM tnt_incidents WHERE tenant_id = :tenantId AND status = 'ESCALATED'")
    Flux<IncidentEntity> findEscalatedByTenantId(UUID tenantId);
    @Query("SELECT COUNT(*) FROM tnt_incidents WHERE agency_id = :agencyId AND status NOT IN ('CLOSED','CANCELLED','RESOLVED')")
    Mono<Long> countActiveByAgencyId(UUID agencyId);
}
