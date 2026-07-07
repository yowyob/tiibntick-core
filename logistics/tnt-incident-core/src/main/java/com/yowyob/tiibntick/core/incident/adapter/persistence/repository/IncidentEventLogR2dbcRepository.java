package com.yowyob.tiibntick.core.incident.adapter.persistence.repository;
import com.yowyob.tiibntick.core.incident.adapter.persistence.entity.IncidentEventLogEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import java.util.UUID;
/**
 * Spring Data R2DBC repository for IncidentEventLog entities.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */

public interface IncidentEventLogR2dbcRepository extends ReactiveCrudRepository<IncidentEventLogEntity, UUID> {
    Flux<IncidentEventLogEntity> findByIncidentIdOrderByOccurredAtAsc(UUID incidentId);
}
