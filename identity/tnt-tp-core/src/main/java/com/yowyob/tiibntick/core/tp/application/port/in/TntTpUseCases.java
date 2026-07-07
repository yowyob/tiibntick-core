package com.yowyob.tiibntick.core.tp.application.port.in;

import com.yowyob.tiibntick.core.tp.application.port.in.command.*;
import com.yowyob.tiibntick.core.tp.domain.model.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Use case: Retrieve a TntClientProfile by its profile ID.
 *
 * @author MANFOUO Braun
 */
interface GetTntClientProfileUseCase {
    Mono<TntClientProfile> getByProfileId(UUID profileId);
    Mono<TntClientProfile> getByThirdPartyId(UUID tenantId, UUID thirdPartyId);
}

/**
 * Use case: Submit KYC documents for identity verification.
 *
 * @author MANFOUO Braun
 */
interface SubmitKycDocumentsUseCase {
    Mono<KycRecord> submit(SubmitKycCommand command);
}

/**
 * Use case: Approve a submitted KYC record.
 *
 * @author MANFOUO Braun
 */
interface ApproveKycUseCase {
    Mono<KycRecord> approve(ApproveKycCommand command);
}

/**
 * Use case: Reject a submitted KYC record with a reason.
 *
 * @author MANFOUO Braun
 */
interface RejectKycUseCase {
    Mono<KycRecord> reject(RejectKycCommand command);
}

/**
 * Use case: Credit loyalty points to a third party account.
 *
 * @author MANFOUO Braun
 */
interface EarnLoyaltyPointsUseCase {
    Mono<LoyaltyAccount> earn(EarnLoyaltyPointsCommand command);
}

/**
 * Use case: Redeem loyalty points for a delivery discount.
 *
 * @author MANFOUO Braun
 */
interface RedeemLoyaltyPointsUseCase {
    Mono<LoyaltyAccount> redeem(RedeemLoyaltyPointsCommand command);
}

/**
 * Use case: Retrieve a third party's loyalty account.
 *
 * @author MANFOUO Braun
 */
interface GetLoyaltyAccountUseCase {
    Mono<LoyaltyAccount> getByThirdPartyId(UUID tenantId, UUID thirdPartyId);
}

/**
 * Use case: Generate and assign a phone alias for hub anonymity.
 *
 * @author MANFOUO Braun
 */
interface GeneratePhoneAliasUseCase {
    Mono<TntClientProfile> generateAlias(GeneratePhoneAliasCommand command);
}

/**
 * Use case: Rate a third party after a delivery.
 *
 * @author MANFOUO Braun
 */
interface RateThirdPartyUseCase {
    Mono<ThirdPartyRating> rate(RateThirdPartyCommand command);
    Flux<ThirdPartyRating> listRatings(UUID tenantId, UUID thirdPartyId);
/**
 * Use case: Link a TntClientProfile to a FreelancerOrganization ().
 * Enables direct client-FreelancerOrg relationship tracking.
 *
 * @author MANFOUO Braun
 */
interface LinkToFreelancerOrgUseCase {
    /**
     * Links the third party profile to a FreelancerOrg.
     * Adds "FREELANCER_ORG" entry to providerLinks and grants FREELANCER_ORG_CLIENT role.
     *
     * @param command the link command
     * @return the updated TntClientProfile
     */
    Mono<TntClientProfile> linkToFreelancerOrg(
            com.yowyob.tiibntick.core.tp.application.port.in.command.LinkToFreelancerOrgCommand command);

    /**
     * Unlinks the third party profile from a FreelancerOrg.
     * Removes "FREELANCER_ORG" entry from providerLinks and revokes FREELANCER_ORG_CLIENT role.
     *
     * @param thirdPartyId   the third party UUID
     * @param tenantId       the tenant scope
     * @param freelancerOrgId the FreelancerOrg UUID to unlink from
     * @return the updated TntClientProfile
     */
    Mono<TntClientProfile> unlinkFromFreelancerOrg(UUID tenantId, UUID thirdPartyId, String freelancerOrgId);
}

/**
 * Use case: Find clients linked to a specific FreelancerOrganization ().
 * Used by the FreelancerOrg owner's dashboard to view their client base.
 *
 * @author MANFOUO Braun
 */
interface FindClientsByFreelancerOrgUseCase {
    /**
     * Returns all TntClientProfiles linked to the given FreelancerOrg.
     *
     * @param tenantId        tenant scope
     * @param freelancerOrgId the FreelancerOrg UUID
     * @return Flux of profiles linked to this FreelancerOrg
     */
    Flux<TntClientProfile> findByFreelancerOrg(UUID tenantId, String freelancerOrgId);

    /**
     * Returns the total number of transactions a client made with a FreelancerOrg.
     * Used by billing DSL to evaluate the {@code clientTxCount} variable for this org.
     *
     * @param tenantId        tenant scope
     * @param thirdPartyId    the client's third party UUID
     * @param freelancerOrgId the FreelancerOrg UUID
     * @return count of delivery transactions
     */
    Mono<Integer> countTransactionsByFreelancerOrg(UUID tenantId, UUID thirdPartyId, String freelancerOrgId);
}

}