package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpFreelancerRepository;
import com.yowyob.tiibntick.core.actor.application.port.out.IFreelancerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Only the user behind a delivery person (or an admin) may change that delivery person's pricing grid
 * and eligibility. Same switch as the delivery-need guard, protected by default.
 */
@Component
public class FreelancerOwnershipGuard {

    private final GofpFreelancerRepository gofpFreelancerRepository;
    private final IFreelancerRepository actorFreelancerRepository;

    @Autowired
    public FreelancerOwnershipGuard(GofpFreelancerRepository gofpFreelancerRepository,
                                    @Nullable IFreelancerRepository actorFreelancerRepository) {
        this.gofpFreelancerRepository = gofpFreelancerRepository;
        this.actorFreelancerRepository = actorFreelancerRepository;
    }

    public FreelancerOwnershipGuard(GofpFreelancerRepository gofpFreelancerRepository) {
        this(gofpFreelancerRepository, null);
    }

    @Value("${tnt.gofp.ownership-guard.enabled:true}")
    private boolean enabled = true;

    /** Completes empty when the caller may act on {@code deliveryPersonId}, errors with 403 otherwise. */
    public Mono<Void> check(UUID deliveryPersonId, UUID callerId, boolean admin) {
        if (!enabled || admin) {
            return Mono.empty();
        }
        if (callerId == null) {
            return Mono.error(new AccessDeniedException(
                    "[ownership] this operation requires an authenticated user identity"));
        }
        return gofpFreelancerRepository.findByCoreFreelancerId(deliveryPersonId)
                .switchIfEmpty(Mono.defer(() -> gofpFreelancerRepository.findById(deliveryPersonId)))
                .filter(f -> callerId.equals(f.getCoreUserId()))
                .map(f -> Boolean.TRUE)
                // The GOFP projection row is only written on some journeys: a freelancer who has just created
                // the profile has none yet. The profile itself says who owns the id, so read it there.
                .switchIfEmpty(Mono.defer(() -> ownsActorProfile(deliveryPersonId, callerId)))
                .flatMap(owns -> owns
                        ? Mono.<Void>empty()
                        : Mono.<Void>error(new AccessDeniedException("This resource belongs to another freelancer")));
    }

    private Mono<Boolean> ownsActorProfile(UUID deliveryPersonId, UUID callerId) {
        if (actorFreelancerRepository == null) {
            return Mono.just(false);
        }
        return actorFreelancerRepository.findFirstByActorId(callerId)
                .map(profile -> deliveryPersonId.equals(profile.id()))
                .defaultIfEmpty(false);
    }
}
