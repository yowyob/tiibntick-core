package com.yowyob.tiibntick.core.organization.infrastructure.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.organization.infrastructure.adapter.out.persistence.entity.FreelancerOrgEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Spring Data R2DBC reactive repository for
 * {@link FreelancerOrgEntity}.
 *
 * <p>Provides basic CRUD operations plus custom derived queries for looking up
 * FreelancerOrganizations by their OWNER actor, tenant ID, and trade name.
 *
 * @author MANFOUO Braun
 */
public interface FreelancerOrgR2dbcRepository
        extends ReactiveCrudRepository<FreelancerOrgEntity, UUID> {

    /**
     * Finds the FreelancerOrganization entity owned by the given actor UUID.
     *
     * @param ownerActorId the OWNER actor UUID
     * @return a reactive stream emitting at most one entity
     */
    Mono<FreelancerOrgEntity> findFirstByOwnerActorIdOrderByCreatedAtDesc(UUID ownerActorId);

    /**
     * Finds the FreelancerOrganization entity with the given tenant ID.
     *
     * @param tenantId the multi-tenant key (prefixed "FRL-")
     * @return a reactive stream emitting at most one entity
     */
    Mono<FreelancerOrgEntity> findByTenantId(String tenantId);

    /**
     * Checks whether a FreelancerOrganization with the given trade name exists.
     * Case-insensitive matching is handled at the SQL level via ILIKE.
     *
     * @param tradeName the commercial trade name to look up
     * @return {@code true} if any entity with that name exists
     */
    Mono<Boolean> existsByTradeNameIgnoreCase(String tradeName);
}
