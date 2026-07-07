package com.yowyob.tiibntick.core.organization.infrastructure.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.organization.infrastructure.adapter.out.persistence.entity.FreelancerOperationalZoneEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Spring Data R2DBC reactive repository for
 * {@link FreelancerOperationalZoneEntity}.
 *
 * @author MANFOUO Braun
 */
public interface FreelancerOperationalZoneR2dbcRepository
        extends ReactiveCrudRepository<FreelancerOperationalZoneEntity, UUID> {

    /**
     * Finds all operational zone rows for a given FreelancerOrganization.
     *
     * @param freelancerOrgId the FreelancerOrganization UUID
     * @return all zone entities for the org
     */
    Flux<FreelancerOperationalZoneEntity> findByFreelancerOrgId(UUID freelancerOrgId);

    /**
     * Deletes all zone rows for a given FreelancerOrganization.
     * Used during save() to replace all zones atomically.
     *
     * @param freelancerOrgId the FreelancerOrganization UUID
     * @return completion signal
     */
    Mono<Void> deleteByFreelancerOrgId(UUID freelancerOrgId);

    /**
     * Finds all FreelancerOrganization UUIDs whose polygon zones contain or overlap
     * the given geographic point within the given radius.
     *
     * <p>Uses PostGIS {@code ST_DWithin} for the proximity search. Distances are
     * computed in degrees (SRID 4326); 1 degree ≈ 111 km at the equator.
     *
     * @param longitude WGS-84 longitude of the search point
     * @param latitude  WGS-84 latitude of the search point
     * @param radiusDeg search radius in degrees (radiusKm / 111.0)
     * @return UUIDs of FreelancerOrganizations with matching zones
     */
    @Query("SELECT DISTINCT freelancer_org_id FROM tnt_freelancer_operational_zone " +
           "WHERE active = true " +
           "AND ST_DWithin(ST_GeomFromText(polygon_wkt, 4326), " +
           "               ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326), :radiusDeg)")
    Flux<UUID> findOrgIdsByProximity(double longitude, double latitude, double radiusDeg);
}
