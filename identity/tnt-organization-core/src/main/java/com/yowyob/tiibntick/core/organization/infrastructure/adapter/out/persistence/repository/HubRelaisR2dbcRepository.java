package com.yowyob.tiibntick.core.organization.infrastructure.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.organization.infrastructure.adapter.out.persistence.entity.HubRelaisEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

import java.util.UUID;

/**
 * Spring Data R2DBC reactive repository for {@link HubRelaisEntity}.
 *
 * <p>Provides basic CRUD operations plus:
 * <ul>
 *   <li>A native PostGIS spatial query for polygon-based hub discovery.</li>
 *   <li>Derived queries by Kernel organization UUID and by tenant.</li>
 * </ul>
 *
 * @author MANFOUO Braun
 */
public interface HubRelaisR2dbcRepository extends ReactiveCrudRepository<HubRelaisEntity, UUID> {

    /**
     * Finds all operational relay hubs whose geographic point (WKT SRID 4326) falls
     * within the given polygon using PostGIS {@code ST_Within}.
     *
     * <p>Query: {@code ST_Within(ST_GeomFromText(geographic_point_wkt, 4326), ST_GeomFromText(:polygonWkt, 4326))}
     *
     * @param polygonWkt the WKT POLYGON string defining the search area (SRID 4326)
     * @return a reactive stream of HubRelais entities within the polygon
     */
    @Query("""
            SELECT * FROM tnt_hub_relais
            WHERE operational = true
              AND ST_Within(
                  ST_GeomFromText(geographic_point_wkt, 4326),
                  ST_GeomFromText(:polygonWkt, 4326)
              )
            """)
    Flux<HubRelaisEntity> findOperationalHubsWithinPolygon(String polygonWkt);

    /**
     * Finds all operational HubRelais entities linked to a Kernel organization.
     *
     * @param organizationId the Kernel organization UUID (integration key)
     * @return a reactive stream of matching operational HubRelais entities
     */
    Flux<HubRelaisEntity> findByOrganizationIdAndOperational(UUID organizationId, boolean operational);

    /**
     * Finds all HubRelais entities for a given tenant.
     *
     * @param tenantId the multi-tenant key
     * @return a reactive stream of all HubRelais entities in the tenant
     */
    Flux<HubRelaisEntity> findByTenantId(UUID tenantId);
}
