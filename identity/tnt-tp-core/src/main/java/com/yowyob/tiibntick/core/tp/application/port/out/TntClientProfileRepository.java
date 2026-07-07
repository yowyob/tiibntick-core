package com.yowyob.tiibntick.core.tp.application.port.out;

import com.yowyob.tiibntick.core.tp.domain.model.TntClientProfile;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Output port: persistence for TntClientProfile.
 *
 * @author MANFOUO Braun
 */
public interface TntClientProfileRepository {

    Mono<TntClientProfile> save(TntClientProfile profile);

    Mono<TntClientProfile> findById(UUID profileId);

    Mono<TntClientProfile> findByThirdPartyId(UUID tenantId, UUID thirdPartyId);

    Mono<Boolean> existsByThirdPartyId(UUID tenantId, UUID thirdPartyId);

    Flux<TntClientProfile> findAllByTenantId(UUID tenantId);

    Mono<Void> deleteById(UUID profileId);
    /**
     * Finds all profiles linked to a specific FreelancerOrg ().
     *
     * @param tenantId        tenant scope
     * @param freelancerOrgId the FreelancerOrg UUID (from tnt-organization-core)
     * @return Flux of profiles with FREELANCER_ORG entry in providerLinks
     */
    Flux<TntClientProfile> findByFreelancerOrgId(UUID tenantId, String freelancerOrgId);

    /**
     * Counts deliveries/transactions between a third party and a FreelancerOrg.
     * Used for DSL clientTxCount variable evaluation per FreelancerOrg context.
     *
     * @param tenantId        tenant scope
     * @param thirdPartyId    the client's third party UUID
     * @param freelancerOrgId the FreelancerOrg UUID
     * @return count of transactions
     */
    Mono<Integer> countTransactionsByFreelancerOrg(UUID tenantId, UUID thirdPartyId, String freelancerOrgId);

}