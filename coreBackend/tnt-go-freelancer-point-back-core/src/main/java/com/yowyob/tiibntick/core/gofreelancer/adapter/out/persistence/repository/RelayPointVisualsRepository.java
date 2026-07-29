package com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.RelayPointVisuals;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;
import java.util.UUID;

public interface RelayPointVisualsRepository extends ReactiveCrudRepository<RelayPointVisuals, UUID> {
    Mono<RelayPointVisuals> findByCoreRelayPointId(UUID coreRelayPointId);
}
