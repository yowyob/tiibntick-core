package com.yowyob.tiibntick.core.incident.adapter.persistence.repository;
import com.yowyob.tiibntick.core.incident.adapter.persistence.entity.IncidentInterAgencyCooperationEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import java.util.UUID;
/**
 * Spring Data R2DBC repository for IncidentInterAgencyCooperation entities.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */

public interface IncidentCooperationR2dbcRepository extends ReactiveCrudRepository<IncidentInterAgencyCooperationEntity, UUID> {
    Flux<IncidentInterAgencyCooperationEntity> findByIncidentId(UUID incidentId);
    @Query("SELECT * FROM tnt_incident_inter_agency_cooperations WHERE responding_agency_id = :agencyId AND status IN ('ACCEPTED','IN_PROGRESS')")
    Flux<IncidentInterAgencyCooperationEntity> findActiveByRespondingAgencyId(UUID agencyId);
}
