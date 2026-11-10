package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpFreelancerRepository;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class FreelancerOwnershipGuard {

    private final GofpFreelancerRepository gofpFreelancerRepository;

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
                .switchIfEmpty(Mono.error(new AccessDeniedException("This resource belongs to another freelancer")))
                .then();
    }
}
