package com.yowyob.tiibntick.core.gofreelancer.domain.port.out;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpRelayPoint;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.relaypoint.RelayPointStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound port for GofpRelayPoint persistence operations.
 */
public interface GofpRelayPointRepository {

    Mono<GofpRelayPoint> save(GofpRelayPoint relayPoint);

    Mono<GofpRelayPoint> findById(UUID id);

    Mono<GofpRelayPoint> findByCoreRelayPointId(UUID coreRelayPointId);

    Flux<GofpRelayPoint> findAllByCoreFreelancerId(UUID coreFreelancerId);

    Flux<GofpRelayPoint> findAllByStatus(RelayPointStatus status);

    Flux<GofpRelayPoint> findAllByIsActive(Boolean isActive);

    Flux<GofpRelayPoint> findAllActiveApproved();

    Flux<GofpRelayPoint> findAll();

    Mono<Void> deleteById(UUID id);

    /**
     * Returns all active/approved relay points that have enough remaining
     * storage capacity to accept a packet of {@code requiredVolumeM3} m³.
     *
     * <p>A relay point is eligible when:
     * <pre>
     *   storageTotalM3 - SUM(active_deposits.packet_volume_m3) >= requiredVolumeM3
     * </pre>
     * where {@code storageTotalM3} is derived from the relay point's own
     * {@code storage_length * storage_width * storage_height} converted to m³,
     * and active deposits are those with status != 'RETRIEVED'.
     *
     * @param requiredVolumeM3 volume of the incoming packet in m³
     * @return relay points with sufficient remaining capacity
     */
    Flux<GofpRelayPoint> findAllWithSufficientCapacity(double requiredVolumeM3);
}
