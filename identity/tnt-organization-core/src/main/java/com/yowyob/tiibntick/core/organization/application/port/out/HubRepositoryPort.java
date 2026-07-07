package com.yowyob.tiibntick.core.organization.application.port.out;

import com.yowyob.tiibntick.core.organization.domain.model.HubRelais;
import com.yowyob.tiibntick.core.organization.domain.vo.OrganizationId;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound persistence port for the {@link HubRelais} aggregate.
 *
 * <p>Defines the reactive contract for persisting and querying HubRelais aggregates.
 * The adapter implementation uses Spring Data R2DBC with PostgreSQL + PostGIS for
 * geospatial queries.
 *
 * <p>This is a secondary port (driven port) in the hexagonal architecture.
 *
 * @author MANFOUO Braun
 */
public interface HubRepositoryPort {

    /**
     * Persists a new HubRelais or updates an existing one.
     *
     * @param hub the HubRelais aggregate to save
     * @return a {@link Mono} emitting the saved HubRelais
     */
    Mono<HubRelais> save(HubRelais hub);

    /**
     * Finds a HubRelais by its TiiBnTick internal ID.
     *
     * @param id the TiiBnTick internal hub ID
     * @return a {@link Mono} emitting the found HubRelais, or empty if not found
     */
    Mono<HubRelais> findById(OrganizationId id);

    /**
     * Returns all HubRelais within a given geographic polygon using PostGIS.
     *
     * <p>Uses the native PostGIS function:
     * <pre>{@code
     *     ST_Within(ST_GeomFromText(geographic_point_wkt, 4326), ST_GeomFromText(:polygonWkt, 4326))
     * }</pre>
     *
     * @param polygonWkt a WKT POLYGON string defining the search area (SRID 4326)
     * @return a {@link Flux} of HubRelais whose geographic point falls within the polygon
     */
    Flux<HubRelais> findWithinPolygon(String polygonWkt);

    /**
     * Returns all operational HubRelais for a given Kernel organization.
     *
     * @param organizationId the Kernel organization UUID
     * @return a {@link Flux} of matching operational HubRelais
     */
    Flux<HubRelais> findOperationalByOrganizationId(UUID organizationId);

    /**
     * Returns all HubRelais for a given tenant.
     *
     * @param tenantId the multi-tenant key
     * @return a {@link Flux} of all HubRelais for the tenant
     */
    Flux<HubRelais> findAllByTenantId(UUID tenantId);
}
