package com.yowyob.tiibntick.core.organization.application.port.out;

import com.yowyob.tiibntick.core.organization.domain.model.FreelancerOrganization;
import com.yowyob.tiibntick.core.organization.domain.vo.AssociatedDelivererRef;
import com.yowyob.tiibntick.core.organization.domain.vo.OrganizationId;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound persistence port for the {@link FreelancerOrganization} aggregate.
 *
 * <p>Defines the reactive contract for persisting and querying FreelancerOrganization
 * aggregates. The adapter implementation uses Spring Data R2DBC with PostgreSQL/PostGIS.
 *
 * <p>This is a secondary port (driven port) in the hexagonal architecture.
 *
 * @author MANFOUO Braun
 */
public interface FreelancerOrgRepositoryPort {

    /**
     * Persists a new FreelancerOrganization or updates an existing one.
     * Also persists the associated zones and sub-deliverer refs.
     *
     * @param org the FreelancerOrganization aggregate to save
     * @return a {@link Mono} emitting the saved aggregate
     */
    Mono<FreelancerOrganization> save(FreelancerOrganization org);

    /**
     * Finds a FreelancerOrganization by its TiiBnTick internal ID.
     *
     * @param id the internal ID
     * @return a {@link Mono} emitting the aggregate with zones and sub-deliverers loaded,
     *         or empty if not found
     */
    Mono<FreelancerOrganization> findById(OrganizationId id);

    /**
     * Finds a FreelancerOrganization by the OWNER's actor UUID.
     *
     * @param ownerActorId the OWNER actor UUID
     * @return a {@link Mono} emitting the aggregate, or empty if not found
     */
    Mono<FreelancerOrganization> findByOwnerActorId(UUID ownerActorId);

    /**
     * Finds a FreelancerOrganization by its unique tenant ID.
     *
     * @param tenantId the multi-tenant key (prefixed "FRL-")
     * @return a {@link Mono} emitting the aggregate, or empty if not found
     */
    Mono<FreelancerOrganization> findByTenantId(String tenantId);

    /**
     * Finds all FreelancerOrganizations whose operational zones are within
     * a given radius of a geographic point.
     *
     * @param latitude  WGS-84 latitude
     * @param longitude WGS-84 longitude
     * @param radiusKm  search radius in kilometres
     * @return a {@link Flux} of matching FreelancerOrganizations
     */
    Flux<FreelancerOrganization> findByZoneProximity(double latitude,
                                                      double longitude,
                                                      double radiusKm);

    /**
     * Checks whether a FreelancerOrganization with the given internal ID exists.
     *
     * @param id the internal ID
     * @return a {@link Mono} emitting {@code true} if found, {@code false} otherwise
     */
    Mono<Boolean> existsById(OrganizationId id);

    /**
     * Checks whether any FreelancerOrganization with the given trade name exists.
     *
     * @param tradeName the trade name to check (case-insensitive)
     * @return a {@link Mono} emitting {@code true} if found
     */
    Mono<Boolean> existsByTradeName(String tradeName);

    /**
     * Loads all sub-deliverer refs for a given org.
     *
     * @param orgId the FreelancerOrganization internal ID
     * @return a {@link Flux} of {@link AssociatedDelivererRef}
     */
    Flux<AssociatedDelivererRef> findSubDeliverersByOrgId(OrganizationId orgId);
}
