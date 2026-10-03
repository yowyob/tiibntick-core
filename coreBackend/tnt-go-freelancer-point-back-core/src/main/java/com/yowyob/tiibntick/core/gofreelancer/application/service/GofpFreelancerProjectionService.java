package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.actor.application.port.out.IFreelancerRepository;
import com.yowyob.tiibntick.core.delivery.application.port.out.DeliveryPersonRepository;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.DeliveryPerson;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpFreelancerRepository;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpFreelancer;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.freelancer.FreelancerStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * Projects a TiiBnTick freelancer profile into the GOFP read model.
 *
 * <p>When a user creates a freelancer profile via {@code POST /api/v1/freelancers}
 * (tnt-actor-core), two GOFP rows must exist before they can subscribe to an
 * announcement:
 * <ol>
 *   <li>{@code gofp_freelancers} — quota + matching metadata, keyed by
 *       {@code id = core_freelancer_id = freelancerProfiles.id}</li>
 *   <li>{@code tnt_delivery_persons} — logistics row consumed by
 *       {@code DeliveryAnnouncementService.respondToAnnouncement}, keyed by
 *       {@code id = actor_id = freelancerProfiles.id}</li>
 * </ol>
 *
 * <p><strong>Invariant enforced:</strong>
 * {@code tnt_actor.freelancer_profiles.id == gofp_freelancers.id
 *     == gofp_freelancers.core_freelancer_id == tnt_delivery_persons.id}.
 *
 * <p><strong>Idempotent:</strong> calling this service multiple times for the same
 * user produces exactly one row per table. An existing row is returned unchanged —
 * a {@code remaining_deliveries} already decremented by usage is never reset.
 *
 * <p><strong>Best-effort:</strong> this service is called from a
 * {@code WebFilter} — errors are swallowed at the call site so that a projection
 * failure never blocks the HTTP request. A Micrometer counter
 * ({@code gofp.freelancer.provisioning.failures}) tracks silent errors. The filter only
 * calls it on the freelancer-journey routes and caches positive answers (lot C-19).
 *
 * <p><strong>TODO (décision métier reportée — validation logistique/KYC du livreur) :</strong>
 * {@code status = APPROVED} is set immediately on both rows so the accept-a-delivery
 * journey is testable end-to-end. Nothing in the code today verifies vehicle papers,
 * insurance or identity before a freelancer can subscribe: this is a deliberate,
 * temporary compromise. The business must decide whether a {@code PENDING} state plus an
 * admin approval step is required before {@code APPROVED}; until then every freelancer
 * profile is auto-approved on the logistics side.
 *
 * @author KOUAM Kamdem
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GofpFreelancerProjectionService {

    /**
     * Initial delivery quota for a newly projected freelancer.
     *
     * <p>100 is the value the E2E harness used when it seeded this row by hand
     * (lot C-14, {@code e2e-freelancer-courier.sh}); keeping it means behaviour is
     * unchanged for anyone who relied on that seed. It is <em>not</em> a value
     * validated by the business: it is a provisional default until a quota/subscription
     * policy decides it (a property such as {@code tnt.gofp.freelancer.initial-quota}
     * would be the natural next step).
     */
    public static final int DEFAULT_INITIAL_QUOTA = 100;

    private final IFreelancerRepository freelancerRepository;
    private final GofpUserProvisioningService userProvisioningService;
    private final GofpFreelancerRepository gofpFreelancerRepository;
    private final DeliveryPersonRepository deliveryPersonRepository;

    /**
     * Ensures the GOFP projection rows exist for {@code coreUserId} in {@code tenantId}.
     *
     * <p>If the user has no {@code FreelancerProfile} in tnt-actor-core, this is a no-op:
     * regular users (clients, etc.) are not freelancers and must not be projected.
     *
     * @param coreUserId the JWT {@code sub} of the current user (= {@code actorId})
     * @param tenantId   the tenant from the JWT
     * @return {@code true} once both projection rows exist, {@code false} if the user has
     *         no freelancer profile; repository errors propagate to the caller
     */
    public Mono<Boolean> projectIfAbsent(UUID coreUserId, UUID tenantId) {
        return freelancerRepository.findByActorId(tenantId, coreUserId)
                .flatMap(profile -> provisionAll(coreUserId, tenantId, profile.id()))
                .defaultIfEmpty(false);
    }

    private Mono<Boolean> provisionAll(UUID coreUserId, UUID tenantId, UUID profileId) {
        return userProvisioningService.provisionIfAbsent(coreUserId)
                .flatMap(user -> ensureGofpFreelancer(profileId, coreUserId))
                .flatMap(ignored -> ensureDeliveryPerson(profileId, coreUserId, tenantId))
                .map(ignored -> true);
    }

    private Mono<GofpFreelancer> ensureGofpFreelancer(UUID profileId, UUID coreUserId) {
        return gofpFreelancerRepository.findById(profileId)
                .switchIfEmpty(Mono.defer(() -> {
                    GofpFreelancer freelancer = GofpFreelancer.builder()
                            .id(profileId)
                            .coreFreelancerId(profileId)
                            .coreUserId(coreUserId)
                            .status(FreelancerStatus.APPROVED) // TODO: see class javadoc — auto-approval pending business decision
                            .isActive(true)
                            .remainingDeliveries(DEFAULT_INITIAL_QUOTA)
                            .createdAt(Instant.now())
                            .updatedAt(Instant.now())
                            .build();
                    log.info("Projecting gofp_freelancers id={} coreUserId={}", profileId, coreUserId);
                    return gofpFreelancerRepository.save(freelancer);
                }));
    }

    private Mono<DeliveryPerson> ensureDeliveryPerson(UUID profileId, UUID coreUserId, UUID tenantId) {
        return deliveryPersonRepository.findById(tenantId, profileId)
                .switchIfEmpty(Mono.defer(() -> {
                    DeliveryPerson dp = DeliveryPerson.projection(profileId, tenantId, coreUserId,
                            DEFAULT_INITIAL_QUOTA);
                    log.info("Projecting tnt_delivery_persons id={} actorId={}", profileId, coreUserId);
                    return deliveryPersonRepository.save(dp);
                }));
    }
}
