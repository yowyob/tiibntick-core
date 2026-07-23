package com.yowyob.tiibntick.core.organization.application.port.in;

import com.yowyob.tiibntick.core.organization.domain.model.HubRelais;
import com.yowyob.tiibntick.core.organization.domain.vo.OrganizationId;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Primary inbound port — HubRelais management use cases.
 *
 * <p>Defines the contract exposed by the application layer for operations on the
 * {@link HubRelais} aggregate. Called by adapters (REST controllers, event consumers).
 *
 * <p>All operations are reactive (Reactor {@link Mono}/{@link Flux}).
 *
 * @author MANFOUO Braun
 */
public interface ManageHubUseCase {

    /**
     * Creates a new relay hub and validates its Kernel organization reference.
     *
     * <p>Precondition: the {@code organizationId} must exist and be active in
     * RT-comops-organization-core (verified via {@code KernelOrganizationPort}).
     *
     * @param organizationId     Kernel organization UUID (must be active)
     * @param tenantId           Multi-tenant key
     * @param name               Relay hub name
     * @param maxParcelCapacity  Maximum parcel storage capacity (must be &gt; 0)
     * @param geographicPointWkt PostGIS WKT POINT string (SRID 4326)
     * @param openingHours       Free-text opening hours
     * @param operatorId         Operator actor UUID (nullable)
     * @return a {@link Mono} emitting the persisted {@link HubRelais}
     */
    Mono<HubRelais> createHub(UUID organizationId,
                              UUID tenantId,
                              String name,
                              int maxParcelCapacity,
                              String geographicPointWkt,
                              String openingHours,
                              UUID operatorId);

    /**
     * Retrieves a relay hub by its TiiBnTick internal ID.
     *
     * @param hubId the TiiBnTick hub ID
     * @return a {@link Mono} emitting the found {@link HubRelais}, or empty
     */
    Mono<HubRelais> findHubById(OrganizationId hubId);

    /**
     * Checks whether a relay hub has available parcel capacity.
     *
     * @param hubId            the TiiBnTick hub ID
     * @param currentOccupancy number of parcels currently stored at this hub
     * @return a {@link Mono} emitting {@code true} if capacity is available
     */
    Mono<Boolean> checkHubCapacity(OrganizationId hubId, int currentOccupancy);

    /**
     * Returns all operational relay hubs within a geographic polygon.
     *
     * <p>Uses PostGIS {@code ST_Within} for spatial filtering.
     *
     * @param polygonWkt WKT POLYGON string defining the search area (SRID 4326)
     * @return a {@link Flux} of HubRelais within the polygon
     */
    Flux<HubRelais> findHubsInZone(String polygonWkt);

    /**
     * Returns all relay hubs for a given tenant.
     *
     * @param tenantId the multi-tenant key
     * @return a {@link Flux} of all HubRelais for the tenant
     */
    Flux<HubRelais> listHubsForTenant(UUID tenantId);

    /**
     * Updates the maximum parcel capacity of a relay hub.
     *
     * @param hubId       the TiiBnTick hub ID
     * @param newCapacity the new capacity (must be strictly positive)
     * @return a {@link Mono} emitting the updated {@link HubRelais}
     */
    Mono<HubRelais> updateCapacity(OrganizationId hubId, int newCapacity);

    /**
     * Assigns or reassigns the operator for a relay hub.
     *
     * @param hubId      the TiiBnTick hub ID
     * @param operatorId the new operator's actor UUID
     * @return a {@link Mono} emitting the updated {@link HubRelais}
     */
    Mono<HubRelais> assignOperator(OrganizationId hubId, UUID operatorId);

    /**
     * Marks a relay hub as temporarily out of service.
     *
     * @param hubId the TiiBnTick hub ID
     * @return a {@link Mono} emitting the updated {@link HubRelais}
     */
    Mono<HubRelais> suspendHub(OrganizationId hubId);

    /**
     * Marks a relay hub as operational again.
     *
     * @param hubId the TiiBnTick hub ID
     * @return a {@link Mono} emitting the updated {@link HubRelais}
     */
    Mono<HubRelais> resumeHub(OrganizationId hubId);
}
