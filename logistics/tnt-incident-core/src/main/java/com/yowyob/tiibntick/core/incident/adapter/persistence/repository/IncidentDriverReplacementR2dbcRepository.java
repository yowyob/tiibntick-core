package com.yowyob.tiibntick.core.incident.adapter.persistence.repository;
import com.yowyob.tiibntick.core.incident.adapter.persistence.entity.IncidentDriverReplacementEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;
import java.util.UUID;
/**
 * Spring Data R2DBC repository for IncidentDriverReplacement entities.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */

public interface IncidentDriverReplacementR2dbcRepository extends ReactiveCrudRepository<IncidentDriverReplacementEntity, UUID> {
    Mono<IncidentDriverReplacementEntity> findByIncidentId(UUID incidentId);
}
