package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.Subscription;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.subscription.SubscriptionType;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.SubscriptionUseCase;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.SubscriptionRepository;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.SubscriptionStatusResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Application layer implementation of the SubscriptionUseCase inbound port.
 * Resolves the subscription of a delivery person and maps it to a rich
 * SubscriptionStatusResponseDTO with all quota and commission details.
 *
 * @author TiiBnTickTeam
 * @date 08/07/2026
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionUseCaseImpl implements SubscriptionUseCase {

    private final SubscriptionRepository subscriptionRepository;

    @Override
    public Mono<SubscriptionStatusResponseDTO> getSubscriptionStatus(UUID freelancerId) {
        return subscriptionRepository.findByFreelancerId(freelancerId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Aucun abonnement trouvé pour le livreur " + freelancerId)))
                .map(this::toStatusResponse)
                .doOnSuccess(dto -> log.debug(
                        "Subscription status fetched for freelancer={} — plan={}, status={}, used={}/{}",
                        freelancerId, dto.getPlan(), dto.getStatus(),
                        dto.getDeliveriesUsed(), dto.getMaxDeliveries()));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Mapping
    // ─────────────────────────────────────────────────────────────────────────

    private SubscriptionStatusResponseDTO toStatusResponse(Subscription sub) {
        SubscriptionType plan  = sub.getSubscriptionType();
        int used               = sub.getDeliveriesUsed() == null ? 0 : sub.getDeliveriesUsed();
        boolean unlimited      = plan.isUnlimited();

        int remaining          = unlimited ? -1 : Math.max(0, plan.getMaxDeliveries() - used);
        int quotaUsagePct      = unlimited ? 0
                : (plan.getMaxDeliveries() > 0
                        ? Math.min(100, (int) Math.round((used * 100.0) / plan.getMaxDeliveries()))
                        : 0);

        return SubscriptionStatusResponseDTO.builder()
                // Identity
                .subscriptionId(sub.getId())
                .freelancerId(sub.getFreelancerId())
                // Plan
                .plan(plan.getValue())
                .status(sub.getStatus().getValue())
                .price(sub.getPrice())
                .paymentMethod(sub.getPaymentMethod() != null
                        ? sub.getPaymentMethod().getValue() : null)
                // Quota
                .maxDeliveries(plan.getMaxDeliveries())
                .unlimited(unlimited)
                .deliveriesUsed(used)
                .deliveriesRemaining(remaining)
                .quotaUsagePercent(quotaUsagePct)
                .resetDate(sub.getResetDate())
                // Commission
                .commissionPercent(plan.getCommissionPercent())
                .netPercent(100.0 - plan.getCommissionPercent())
                // Validity
                .startDate(sub.getStartDate())
                .endDate(sub.getEndDate())
                .build();
    }

    @Override
    public Mono<SubscriptionStatusResponseDTO> updateSubscriptionPrice(UUID freelancerId, Float newPrice) {
        return subscriptionRepository.findByFreelancerId(freelancerId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Aucun abonnement trouvé pour le livreur " + freelancerId)))
                .flatMap(sub -> {
                    sub.setPrice(newPrice);
                    return subscriptionRepository.save(sub);
                })
                .map(this::toStatusResponse)
                .doOnSuccess(dto -> log.debug("Subscription price updated for freelancer={} to {}", freelancerId, newPrice));
    }
}
