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
     * Active, approved relay points whose remaining storage volume (m³) is at least the given volume.
     *
     * <p>Hangar volume: the relay point's dimensions converted to m³ with its unit (cm → ÷ 1e6, mm → ÷ 1e9,
     * m or none → as is). Occupied volume: the parcels of the hub's inventory entries not yet picked up
     * ({@code tnt_hub_package_entries}, {@code package_id} = core parcel id), {@code tnt_parcels} dimensions in
     * centimetres → ÷ 1e6. Relay points with an unknown dimension are excluded.</p>
     */
    String SUFFICIENT_CAPACITY_SQL = """
            SELECT rp.*
            FROM gofp_relay_points rp
            LEFT JOIN (
                SELECT e.hub_id,
                       COALESCE(SUM(p.length_cm * p.width_cm * p.height_cm), 0) / 1000000.0 AS occupied_m3
                FROM tnt_hub_package_entries e
                JOIN tnt_parcels p ON p.id = e.package_id
                WHERE e.picked_up_at IS NULL
                GROUP BY e.hub_id
            ) d ON d.hub_id = rp.core_relay_point_id
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
            """;

    /** @param requiredVolumeM3 volume of the incoming parcel in m³ */
    @Query(SUFFICIENT_CAPACITY_SQL)
    Flux<GofpRelayPoint> findAllWithSufficientCapacity(double requiredVolumeM3);
}
