package com.yowyob.tiibntick.core.organization.infrastructure.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.organization.infrastructure.adapter.out.persistence.entity.FreelancerSubDelivererEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Spring Data R2DBC reactive repository for
 * {@link FreelancerSubDelivererEntity}.
 *
 * @author MANFOUO Braun
 */
public interface FreelancerSubDelivererR2dbcRepository
        extends ReactiveCrudRepository<FreelancerSubDelivererEntity, UUID> {

    /**
     * Finds all sub-deliverer rows for a given FreelancerOrganization.
     *
     * @param freelancerOrgId the FreelancerOrganization UUID
     * @return all associated sub-deliverer entities
     */
    Flux<FreelancerSubDelivererEntity> findByFreelancerOrgId(UUID freelancerOrgId);

    /**
     * Deletes all sub-deliverer rows for a given FreelancerOrganization.
     * Used during save() to replace all associations atomically.
     *
     * @param freelancerOrgId the FreelancerOrganization UUID
     * @return completion signal
     */
    Mono<Void> deleteByFreelancerOrgId(UUID freelancerOrgId);
}
