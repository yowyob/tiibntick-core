package com.yowyob.tiibntick.core.actor.application.port.in;

import com.yowyob.tiibntick.core.actor.domain.model.FreelancerProfile;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Primary inbound port — querying FreelancerProfiles by their org membership.
 *
 * <p>Provides queries that resolve actor profiles from FreelancerOrganization context,
 * used by tnt-delivery-core for vehicle selection and mission assignment.
 *
 * @author MANFOUO Braun
 */
public interface IFindFreelancerByOrgUseCase {

    /**
     * Returns all SUB_DELIVERER profiles associated with the given FreelancerOrganization.
     *
     * @param orgId UUID of the FreelancerOrganization
     * @return a {@link Flux} of sub-deliverer profiles
     */
    Flux<FreelancerProfile> findSubDeliverersByOrg(UUID orgId);

    /**
     * Returns the OWNER profile of the given FreelancerOrganization.
     *
     * @param orgId UUID of the FreelancerOrganization
     * @return a {@link Mono} emitting the OWNER profile, or empty if not found
     */
    Mono<FreelancerProfile> findOwnerByOrg(UUID orgId);

    /**
     * Finds a specific actor profile within a given FreelancerOrganization.
     *
     * @param actorId actor UUID to find
     * @param orgId   UUID of the FreelancerOrganization
     * @return a {@link Mono} emitting the profile, or empty if not found
     */
    Mono<FreelancerProfile> findByActorIdAndOrg(UUID actorId, UUID orgId);
}
