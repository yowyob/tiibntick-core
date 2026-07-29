package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.RelayPointSubscriptionRequestDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.RelayPointSubscriptionStatusDTO;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.RelayPointSubscription;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.subscription.SubscriptionStatus;
import com.yowyob.tiibntick.core.gofreelancer.domain.port.in.RelayPointSubscriptionUseCase;
import com.yowyob.tiibntick.core.gofreelancer.domain.port.out.RelayPointSubscriptionRepository;
import com.yowyob.tiibntick.core.gofreelancer.domain.port.out.PricingCatalogPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Application service implementing RelayPointSubscriptionUseCase.
 *
 * @author François-Charles ATANGA
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RelayPointSubscriptionApplicationService implements RelayPointSubscriptionUseCase {

    private final RelayPointSubscriptionRepository subscriptionRepository;
    private final PricingCatalogPort pricingCatalogPort;

    @Override
    public Mono<RelayPointSubscriptionStatusDTO> getSubscriptionStatus(UUID relayPointId) {
        return subscriptionRepository.findByRelayPointId(relayPointId)
                .map(this::toDTO)
                .switchIfEmpty(Mono.error(new IllegalArgumentException(
                        "No subscription found for relay point: " + relayPointId)));
    }

    @Override
    public Mono<RelayPointSubscriptionStatusDTO> createOrRenew(UUID relayPointId,
            RelayPointSubscriptionRequestDTO request) {
        return subscriptionRepository.findByRelayPointId(relayPointId)
                .defaultIfEmpty(RelayPointSubscription.builder()
                        .id(UUID.randomUUID())
                        .relayPointId(relayPointId)
                        .depositsUsed(0)
                        .build())
                .flatMap(sub -> pricingCatalogPort.getSubscriptionPrice(request.getSubscriptionType().name(), "CM")
                        .flatMap(money -> {
                            sub.setSubscriptionType(request.getSubscriptionType());
                            sub.setPaymentMethod(request.getPaymentMethod());
                            sub.setStatus(SubscriptionStatus.ACTIVE);
                            sub.setStartDate(Instant.now());
                            sub.setEndDate(Instant.now().plus(30, ChronoUnit.DAYS));
                            sub.setPrice(money.amount().floatValue());
                            sub.setCurrency(money.currencyCode());
                            return subscriptionRepository.save(sub);
                        }))
                .map(this::toDTO);
    }

    @Override
    public Mono<RelayPointSubscriptionStatusDTO> cancel(UUID relayPointId) {
        return subscriptionRepository.findByRelayPointId(relayPointId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException(
                        "No subscription found for relay point: " + relayPointId)))
                .flatMap(sub -> {
                    sub.setStatus(SubscriptionStatus.CANCELLED);
                    return subscriptionRepository.save(sub);
                })
                .map(this::toDTO);
    }

    @Override
    public Mono<Boolean> isEligible(UUID relayPointId) {
        return subscriptionRepository.findByRelayPointId(relayPointId)
                .map(sub -> sub.getStatus() == SubscriptionStatus.ACTIVE)
                .defaultIfEmpty(false);
    }

    private RelayPointSubscriptionStatusDTO toDTO(RelayPointSubscription sub) {
        return RelayPointSubscriptionStatusDTO.builder()
                .subscriptionId(sub.getId())
                .relayPointId(sub.getRelayPointId())
                .plan(sub.getSubscriptionType() != null ? sub.getSubscriptionType().name() : null)
                .status(sub.getStatus() != null ? sub.getStatus().name() : null)
                .price(sub.getPrice())
                .paymentMethod(sub.getPaymentMethod() != null ? sub.getPaymentMethod().name() : null)
                .depositsUsed(sub.getDepositsUsed() != null ? sub.getDepositsUsed() : 0)
                .startDate(sub.getStartDate())
                .endDate(sub.getEndDate())
                .eligible(sub.getStatus() == SubscriptionStatus.ACTIVE)
                .build();
    }
}
