package com.yowyob.tiibntick.core.actor.application.port.in;

import com.yowyob.tiibntick.core.actor.application.command.LinkFreelancerOrgCommand;
import com.yowyob.tiibntick.core.actor.application.command.UnlinkFreelancerOrgCommand;
import com.yowyob.tiibntick.core.actor.domain.model.FreelancerProfile;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Primary inbound port — FreelancerOrganization link management.
 *
 * <p>Exposes operations to link/unlink a {@link FreelancerProfile} actor to
 * a FreelancerOrganization and to update the cached org verification status.
 *
 * <p>This port is implemented by {@code FreelancerOrgLinkService} and driven by:
 * <ul>
 *   <li>The {@code FreelancerOrgEventConsumer} (Kafka adapter) for event-driven linking.</li>
 *   <li>REST controllers for manual admin operations (if needed).</li>
 * </ul>
 *
 * @author MANFOUO Braun
 */
public interface ILinkFreelancerOrgUseCase {

    /**
     * Links a FreelancerProfile to a FreelancerOrganization.
     *
     * @param command the link command containing actorId, orgId, role, and verification status
     * @return the updated {@link FreelancerProfile} with org link set
     */
    Mono<FreelancerProfile> linkToFreelancerOrg(LinkFreelancerOrgCommand command);

    /**
     * Removes the FreelancerOrganization link from a FreelancerProfile.
     *
     * @param command the unlink command containing actorId and orgId
     * @return the updated {@link FreelancerProfile} with org link cleared
     */
    Mono<FreelancerProfile> unlinkFromFreelancerOrg(UnlinkFreelancerOrgCommand command);

    /**
     * Updates the cached org verification status for all actor profiles
     * linked to the given FreelancerOrganization.
     *
     * <p>Called when {@code tnt.freelancer_org.verified} is received. Updates
     * both the OWNER's profile and all active sub-deliverer profiles.
     *
     * @param orgId    UUID of the verified FreelancerOrganization
     * @param verified {@code true} when the org has been verified
     * @return empty Mono on success
     */
    Mono<Void> updateOrgVerificationStatus(UUID orgId, boolean verified);
}
