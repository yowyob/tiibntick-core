package com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpRelayPoint;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.relaypoint.RelayPointStatus;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Reactive R2DBC repository for GofpRelayPoint entity.
 */
public interface GofpRelayPointR2dbcRepository extends ReactiveCrudRepository<GofpRelayPoint, UUID> {

    Mono<GofpRelayPoint> findByCoreRelayPointId(UUID coreRelayPointId);

    Flux<GofpRelayPoint> findAllByCoreFreelancerId(UUID coreFreelancerId);

    Flux<GofpRelayPoint> findAllByStatus(RelayPointStatus status);

    Flux<GofpRelayPoint> findAllByIsActive(Boolean isActive);

    Flux<GofpRelayPoint> findAllByStatusAndIsActive(RelayPointStatus status, Boolean isActive);

    /**
     * Returns active/approved relay points that have enough remaining storage
     * capacity for a packet of the given volume (m³).
     *
     * <p>Logic:
     * <ol>
     *   <li>Convert the relay point's hangar dimensions to m³ using its unit
     *       (cm → divide by 1 000 000 ; m → no conversion).</li>
     *   <li>Sum {@code packets.length * packets.width * packets.height} (already in m)
     *       for all non-retrieved deposits linked to each relay point.</li>
     *   <li>Keep only those where (totalCapacity - occupiedVolume) >= requiredVolumeM3.</li>
     * </ol>
     *
     * <p>Relay points with NULL dimensions are excluded (capacity unknown).
     *
     * @param requiredVolumeM3 volume of the incoming packet in m³ (dimensions already in m)
     */
    @Query("""
            SELECT rp.*
            FROM gofp_relay_points rp
            LEFT JOIN (
                SELECT rd.logistics_id,
                       COALESCE(SUM(p.length * p.width * p.height), 0) AS occupied_m3
                FROM relay_deposits rd
                JOIN packets p ON p.id = rd.packet_id
                WHERE rd.status != 'RETRIEVED'
                GROUP BY rd.logistics_id
            ) d ON d.logistics_id = rp.core_relay_point_id
            WHERE rp.status = 'APPROVED'
              AND rp.is_active = TRUE
              AND rp.storage_length IS NOT NULL
              AND rp.storage_width  IS NOT NULL
              AND rp.storage_height IS NOT NULL
              AND (
                CASE
                  WHEN LOWER(COALESCE(rp.storage_dimension_unit, 'm')) = 'cm'
                    THEN (rp.storage_length * rp.storage_width * rp.storage_height) / 1000000.0
                  WHEN LOWER(COALESCE(rp.storage_dimension_unit, 'm')) = 'mm'
                    THEN (rp.storage_length * rp.storage_width * rp.storage_height) / 1000000000.0
                  ELSE
                    rp.storage_length * rp.storage_width * rp.storage_height
                END
                - COALESCE(d.occupied_m3, 0)
              ) >= :requiredVolumeM3
            """)
    Flux<GofpRelayPoint> findAllWithSufficientCapacity(double requiredVolumeM3);
}
