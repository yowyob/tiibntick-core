package com.yowyob.tiibntick.core.incident.adapter.persistence.repository;
import com.yowyob.tiibntick.core.incident.adapter.persistence.entity.ParcelIncidentLinkEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.UUID;
/**
 * Spring Data R2DBC repository for ParcelIncidentLink entities.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */

public interface ParcelIncidentLinkR2dbcRepository extends ReactiveCrudRepository<ParcelIncidentLinkEntity, UUID> {
    Flux<ParcelIncidentLinkEntity> findByIncidentId(UUID incidentId);
    Mono<ParcelIncidentLinkEntity> findByParcelIdAndIncidentId(UUID parcelId, UUID incidentId);
}
