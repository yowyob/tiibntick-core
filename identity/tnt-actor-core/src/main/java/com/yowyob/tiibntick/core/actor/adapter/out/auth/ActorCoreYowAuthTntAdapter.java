package com.yowyob.tiibntick.core.actor.adapter.out.auth;

import com.yowyob.tiibntick.core.actor.application.port.out.IDelivererRepository;
import com.yowyob.tiibntick.core.actor.application.port.out.IFreelancerRepository;
import com.yowyob.tiibntick.core.auth.application.port.out.IYowAuthTntAdapter;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.UUID;

/**
 * tnt-actor-core implementation of {@link IYowAuthTntAdapter} (outbound port from tnt-auth-core).
 *
 * <p>This adapter closes the loop between the security bridge (tnt-auth-core) and the actor
 * profile layer (tnt-actor-core). It answers three questions that tnt-auth-core needs to build
 * a fully-enriched {@code TntSecurityContext}:
 * <ol>
 *   <li><strong>resolveActorId</strong> — does this authenticated user have a delivery actor
 *       profile? If so, what is their canonical actorId?</li>
 *   <li><strong>isFreelancer</strong> — is this actor a freelancer (responds to announcements)
 *       rather than a permanent agency deliverer?</li>
 *   <li><strong>resolveAgencyId</strong> — what agency is this actor attached to?</li>
 * </ol>
 *
 * <p>When tnt-auth-core is absent from the classpath (test environments), its
 * {@code NoOpYowAuthTntAdapter} is auto-registered instead of this bean.
 * The {@code @ConditionalOnMissingBean(name = "noOpYowAuthTntAdapter")} ensures
 * this implementation takes precedence when tnt-actor-core is assembled in tnt-bootstrap.
 *
 * @author MANFOUO Braun
 * @see IYowAuthTntAdapter
 */
@Slf4j
@Component
@ConditionalOnMissingBean(name = "noOpYowAuthTntAdapter")
@ConditionalOnBean({IDelivererRepository.class, IFreelancerRepository.class})
public class ActorCoreYowAuthTntAdapter implements IYowAuthTntAdapter {

    private final IDelivererRepository delivererRepository;
    private final IFreelancerRepository freelancerRepository;

    public ActorCoreYowAuthTntAdapter(IDelivererRepository delivererRepository,
                                       IFreelancerRepository freelancerRepository) {
        this.delivererRepository = delivererRepository;
        this.freelancerRepository = freelancerRepository;
    }

    /**
     * Resolves the actor profile ID linked to the given userId within a tenant.
     *
     * <p>In the current TiiBnTick design, the deliverer profile stores the
     * authenticated user's UUID directly as {@code actor_id}. This method confirms
     * that a deliverer profile exists for the user and returns the actorId.
     *
     * <p>The resolution checks deliverer profiles first (most common case).
     * Freelancer profiles use the same actor_id = userId convention.
     *
     * @param userId   the JWT subject UUID
     * @param tenantId the tenant scope from JWT claim {@code tid}
     * @return the actorId if a profile exists, {@link Optional#empty()} otherwise
     */
    @Override
    public Mono<Optional<UUID>> resolveActorId(UUID userId, UUID tenantId) {
        return delivererRepository.findActorIdByUserId(userId, tenantId)
                .map(Optional::of)
                .switchIfEmpty(
                        // Fallback: check if user is a freelancer
                        freelancerRepository.existsByActorId(tenantId, userId)
                                .filter(exists -> exists)
                                .map(exists -> Optional.of(userId))
                                .defaultIfEmpty(Optional.empty())
                )
                .doOnNext(opt -> {
                    if (opt.isPresent()) {
                        log.debug("Resolved actorId {} for userId {} in tenant {}",
                                opt.get(), userId, tenantId);
                    }
                });
    }

    /**
     * Returns true if the given actorId belongs to a freelancer profile within a tenant.
     *
     * <p>Used by tnt-auth-core to set the {@code freelancer} flag on {@code TntSecurityContext},
     * enabling downstream services to apply announcement-specific business rules.
     *
     * @param actorId  the actor UUID (JWT claim {@code actor})
     * @param tenantId the tenant scope
     * @return true if the actor is a freelancer
     */
    @Override
    public Mono<Boolean> isFreelancer(UUID actorId, UUID tenantId) {
        return freelancerRepository.existsByActorId(tenantId, actorId);
    }

    /**
     * Resolves the agency ID attached to the given actorId within a tenant.
     *
     * <p>Only permanent deliverers have an agency. Freelancers, clients and relay operators
     * return {@link Optional#empty()} from this method, indicating they are not bound to a
     * specific agency.
     *
     * @param actorId  the actor UUID
     * @param tenantId the tenant scope
     * @return the agency UUID if the actor is a permanent deliverer, empty otherwise
     */
    @Override
    public Mono<Optional<UUID>> resolveAgencyId(UUID actorId, UUID tenantId) {
        return delivererRepository.findAgencyIdByActorId(actorId, tenantId)
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty());
    }
}
