package com.yowyob.tiibntick.core.delivery.adapter.out.persistence.repository;

import org.springframework.data.r2dbc.repository.Query;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import com.yowyob.tiibntick.core.delivery.adapter.out.persistence.entity.ParcelEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import java.util.UUID;

/**
 * Spring Data R2DBC repository for {@code ParcelEntity}.
 *
 * @author MANFOUO Braun
 */
public interface R2dbcParcelRepository extends ReactiveCrudRepository<ParcelEntity, UUID> {

    /** D-136: a parcel by its tracking code. */
    @Query("SELECT * FROM tnt_parcels WHERE tracking_code = :trackingCode LIMIT 1")
    Mono<ParcelEntity> findByTrackingCode(String trackingCode);

    /** Parcels registered before D-136 that have no tracking code yet (backfill). */
    @Query("SELECT * FROM tnt_parcels WHERE tracking_code IS NULL ORDER BY created_at LIMIT :limit")
    Flux<ParcelEntity> findWithoutTrackingCode(int limit);

    /** Parcels with more than one distinct legacy delivery code (not migrated automatically, reported). */
    @Query("""
            SELECT COUNT(*) FROM (
              SELECT parcel_id FROM tnt_deliveries
              WHERE tracking_code IS NOT NULL
              GROUP BY parcel_id HAVING COUNT(DISTINCT tracking_code) > 1) multi
            """)
    Mono<Long> countParcelsWithSeveralLegacyCodes();
}
