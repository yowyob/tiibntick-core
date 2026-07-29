package com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.OpeningHours;
import java.util.UUID;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive R2DBC repository for OpeningHours entity.
 */
public interface OpeningHoursRepository extends ReactiveCrudRepository<OpeningHours, UUID> {

    Flux<OpeningHours> findAllByRelayPointId(UUID relayPointId);

    Mono<Void> deleteAllByRelayPointId(UUID relayPointId);
}
