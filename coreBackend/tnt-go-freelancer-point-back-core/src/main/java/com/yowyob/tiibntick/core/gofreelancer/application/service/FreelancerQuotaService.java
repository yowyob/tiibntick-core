package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.subscription.SubscriptionStatus;
import com.yowyob.tiibntick.core.gofreelancer.domain.port.out.GofpFreelancerRepository;
import com.yowyob.tiibntick.core.gofreelancer.domain.port.out.RelayPointSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Service responsible for checking whether a freelancer still has a valid
 * delivery quota, equivalent to ATANGA's {@code CommissionService#hasRemainingQuota}.
 *
 * <p>Logic (in order):
 * <ol>
 *   <li>Load the {@code GofpFreelancer} row by ID.</li>
 *   <li>If {@code remainingDeliveries} is set and {@literal > 0} → quota available.</li>
 *   <li>Otherwise, resolve the linked {@code RelayPointSubscription} (re-used for
 *       freelancers until a dedicated FreelancerSubscription table exists):
 *       subscription must be {@code ACTIVE} and {@code depositsUsed < plan.maxDeposits}.</li>
 *   <li>No freelancer / no subscription → returns {@code false} (reject by default).</li>
 * </ol>
 *
 * @author François-Charles ATANGA
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FreelancerQuotaService {

    private final GofpFreelancerRepository freelancerRepository;
    private final RelayPointSubscriptionRepository subscriptionRepository;

    /**
     * Returns {@code true} if the freelancer can still accept a new delivery.
     *
     * @param freelancerId the local GofpFreelancer UUID (not the core freelancer UUID)
     * @return Mono&lt;Boolean&gt; — true if quota remaining, false otherwise
     */
    public Mono<Boolean> hasRemainingQuota(UUID freelancerId) {
        return freelancerRepository.findById(freelancerId)
                .switchIfEmpty(freelancerRepository.findByCoreFreelancerId(freelancerId))
                .flatMap(freelancer -> {
                    // Fast path: remainingDeliveries is maintained directly on GofpFreelancer
                    if (freelancer.getRemainingDeliveries() != null) {
                        boolean hasQuota = freelancer.getRemainingDeliveries() > 0;
                        log.debug("[Quota] Freelancer {} — remainingDeliveries={} → {}",
                                freelancerId, freelancer.getRemainingDeliveries(),
                                hasQuota ? "ELIGIBLE" : "QUOTA_EXHAUSTED");
                        return Mono.just(hasQuota);
                    }

                    // Fallback: check via subscription
                    if (freelancer.getSubscriptionId() == null) {
                        log.warn("[Quota] Freelancer {} has no subscription — rejecting", freelancerId);
                        return Mono.just(false);
                    }

                    return subscriptionRepository.findById(freelancer.getSubscriptionId())
                            .map(sub -> {
                                if (sub.getStatus() != SubscriptionStatus.ACTIVE) {
                                    log.warn("[Quota] Freelancer {} subscription {} is {} — rejecting",
                                            freelancerId, sub.getId(), sub.getStatus());
                                    return false;
                                }
                                // Use RelayPointSubscriptionType quota logic
                                if (sub.getSubscriptionType() == null) return false;
                                int used = sub.getDepositsUsed() != null ? sub.getDepositsUsed() : 0;
                                boolean hasQuota = sub.getSubscriptionType().hasRemainingQuota(used);
                                log.debug("[Quota] Freelancer {} — plan={} used={}/{} → {}",
                                        freelancerId, sub.getSubscriptionType().getValue(),
                                        used, sub.getSubscriptionType().getMaxDeposits(),
                                        hasQuota ? "ELIGIBLE" : "QUOTA_EXHAUSTED");
                                return hasQuota;
                            })
                            .defaultIfEmpty(false);
                })
                .defaultIfEmpty(false)
                .onErrorResume(e -> {
                    log.error("[Quota] Error checking quota for freelancer {}: {}", freelancerId, e.getMessage());
                    return Mono.just(false);
                });
    }
}
