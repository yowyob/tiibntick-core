package com.yowyob.tiibntick.core.tp.application.service;

import com.yowyob.tiibntick.core.tp.application.port.in.command.LinkToFreelancerOrgCommand;
import com.yowyob.tiibntick.core.tp.application.port.out.TntClientProfileRepository;
import com.yowyob.tiibntick.core.tp.application.port.out.TntTpEventPublisher;
import com.yowyob.tiibntick.core.tp.domain.model.TntClientProfile;
import com.yowyob.tiibntick.core.roles.adapter.in.web.RequirePermission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Application service for FreelancerOrg client relationship management.
 *
 * <p>Manages the provider links between {@link TntClientProfile} entities and
 * FreelancerOrganizations. Enables a client to be linked to a FreelancerOrg,
 * allowing the org to see their client base and the billing DSL to resolve
 * {@code isRecurringClient} per org context.
 *
 * <p><b>Kernel integration principle:</b> The {@code freelancerOrgId} is stored as
 * a plain UUID String — no Java class from tnt-organization-core is imported.
 * All org data is referenced via integration key only.
 *
 * @author MANFOUO Braun
 */
@Service
public class FreelancerOrgClientService {

    private static final Logger log = LoggerFactory.getLogger(FreelancerOrgClientService.class);

    private final TntClientProfileRepository profileRepository;
    private final TntTpEventPublisher eventPublisher;

    public FreelancerOrgClientService(TntClientProfileRepository profileRepository,
                                       TntTpEventPublisher eventPublisher) {
        this.profileRepository = profileRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Links a TntClientProfile to a FreelancerOrganization.
     *
     * <p>Adds {@code FREELANCER_ORG: {orgId}} to the profile's {@code providerLinks}
     * and grants the {@code FREELANCER_ORG_CLIENT} role.
     *
     * @param command the link command
     * @return the updated TntClientProfile
     */
    @RequirePermission(resource = "actor", action = "write")
    public Mono<TntClientProfile> linkToFreelancerOrg(LinkToFreelancerOrgCommand command) {
        log.info("Linking thirdPartyId={} to FreelancerOrg={} tenant={}",
                command.thirdPartyId(), command.freelancerOrgId(), command.tenantId());
        return profileRepository.findByThirdPartyId(command.tenantId(), command.thirdPartyId())
                .switchIfEmpty(Mono.error(new IllegalArgumentException(
                        "TntClientProfile not found for thirdPartyId=" + command.thirdPartyId())))
                .map(profile -> profile.linkToFreelancerOrg(command.freelancerOrgId()))
                .flatMap(profileRepository::save)
                .doOnSuccess(p -> log.info("Linked thirdPartyId={} to FreelancerOrg={}",
                        command.thirdPartyId(), command.freelancerOrgId()));
    }

    /**
     * Unlinks a TntClientProfile from a FreelancerOrganization.
     * Removes {@code FREELANCER_ORG} from {@code providerLinks} and revokes the role.
     *
     * @param tenantId        tenant scope
     * @param thirdPartyId    the client's third party UUID
     * @param freelancerOrgId the FreelancerOrg UUID to unlink from
     * @return the updated TntClientProfile
     */
    @RequirePermission(resource = "actor", action = "write")
    public Mono<TntClientProfile> unlinkFromFreelancerOrg(UUID tenantId, UUID thirdPartyId,
                                                           String freelancerOrgId) {
        log.info("Unlinking thirdPartyId={} from FreelancerOrg={}", thirdPartyId, freelancerOrgId);
        return profileRepository.findByThirdPartyId(tenantId, thirdPartyId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException(
                        "TntClientProfile not found for thirdPartyId=" + thirdPartyId)))
                .map(profile -> profile.unlinkFromFreelancerOrg(freelancerOrgId))
                .flatMap(profileRepository::save);
    }

    /**
     * Returns all TntClientProfiles linked to the given FreelancerOrg.
     *
     * @param tenantId        tenant scope
     * @param freelancerOrgId the FreelancerOrg UUID
     * @return Flux of linked profiles
     */
    @RequirePermission(resource = "actor", action = "read")
    public Flux<TntClientProfile> findClientsByFreelancerOrg(UUID tenantId, String freelancerOrgId) {
        log.debug("Finding clients for FreelancerOrg={} tenant={}", freelancerOrgId, tenantId);
        return profileRepository.findByFreelancerOrgId(tenantId, freelancerOrgId);
    }

    /**
     * Returns the number of delivery transactions a client made with a FreelancerOrg.
     *
     * @param tenantId        tenant scope
     * @param thirdPartyId    the client's third party UUID
     * @param freelancerOrgId the FreelancerOrg UUID
     * @return transaction count
     */
    public Mono<Integer> countTransactionsByFreelancerOrg(UUID tenantId, UUID thirdPartyId,
                                                           String freelancerOrgId) {
        return profileRepository.countTransactionsByFreelancerOrg(tenantId, thirdPartyId, freelancerOrgId);
    }
}
