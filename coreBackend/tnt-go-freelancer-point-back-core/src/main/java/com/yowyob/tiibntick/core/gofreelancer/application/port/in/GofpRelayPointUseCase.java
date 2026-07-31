package com.yowyob.tiibntick.core.gofreelancer.application.port.in;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpRelayPoint;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.relaypoint.RelayPointStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Inbound port for GofpRelayPoint operations.
 */
public interface GofpRelayPointUseCase {

    Mono<GofpRelayPoint> createOrUpdate(GofpRelayPoint relayPoint);

    Mono<GofpRelayPoint> findById(UUID id);

    Mono<GofpRelayPoint> findByCoreRelayPointId(UUID coreRelayPointId);

    Flux<GofpRelayPoint> findByFreelancer(UUID coreFreelancerId);

    Flux<GofpRelayPoint> findAll();

    Flux<GofpRelayPoint> findActiveApproved();

    Flux<GofpRelayPoint> findByStatus(RelayPointStatus status);

    Mono<GofpRelayPoint> updateStatus(UUID id, RelayPointStatus status);

    Mono<GofpRelayPoint> setActive(UUID id, Boolean active);

    /** Syncs denormalised owner contact fields from GofpUser. */
    Mono<GofpRelayPoint> syncOwnerContact(UUID coreRelayPointId);

    Mono<Void> delete(UUID id);

    /**
     * Returns active/approved relay points that have enough remaining
     * storage capacity to accept a packet of the given volume (m³).
     *
     * <p>Relay points with no dimensions configured are excluded.
     *
     * @param requiredVolumeM3 volume of the incoming packet in m³ (must be > 0)
     * @return relay points with sufficient remaining capacity, hydrated with owner
     */
    Flux<GofpRelayPoint> findWithSufficientCapacity(double requiredVolumeM3);
}
