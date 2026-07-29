package com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence;

import com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository.GofpRelayPointR2dbcRepository;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpRelayPoint;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.relaypoint.RelayPointStatus;
import com.yowyob.tiibntick.core.gofreelancer.domain.port.out.GofpRelayPointRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound adapter: bridges the domain GofpRelayPointRepository port to the R2DBC repository.
 */
@Component
@RequiredArgsConstructor
public class GofpRelayPointRepositoryAdapter implements GofpRelayPointRepository {

    private final GofpRelayPointR2dbcRepository r2dbcRepository;

    @Override public Mono<GofpRelayPoint> save(GofpRelayPoint rp) { return r2dbcRepository.save(rp); }

    @Override public Mono<GofpRelayPoint> findById(UUID id) { return r2dbcRepository.findById(id); }

    @Override public Mono<GofpRelayPoint> findByCoreRelayPointId(UUID id) { return r2dbcRepository.findByCoreRelayPointId(id); }

    @Override public Flux<GofpRelayPoint> findAllByCoreFreelancerId(UUID id) { return r2dbcRepository.findAllByCoreFreelancerId(id); }

    @Override public Flux<GofpRelayPoint> findAllByStatus(RelayPointStatus s) { return r2dbcRepository.findAllByStatus(s); }

    @Override public Flux<GofpRelayPoint> findAllByIsActive(Boolean a) { return r2dbcRepository.findAllByIsActive(a); }

    @Override public Flux<GofpRelayPoint> findAllActiveApproved() {
        return r2dbcRepository.findAllByStatusAndIsActive(RelayPointStatus.APPROVED, true);
    }

    @Override public Flux<GofpRelayPoint> findAll() { return r2dbcRepository.findAll(); }

    @Override public Mono<Void> deleteById(UUID id) { return r2dbcRepository.deleteById(id); }

    @Override
    public Flux<GofpRelayPoint> findAllWithSufficientCapacity(double requiredVolumeM3) {
        return r2dbcRepository.findAllWithSufficientCapacity(requiredVolumeM3);
    }
}
