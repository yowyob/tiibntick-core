package com.yowyob.tiibntick.core.gofp.application.service;

import com.yowyob.tiibntick.core.gofp.adapter.out.persistence.entity.SubscriptionEntity;
import com.yowyob.tiibntick.core.gofp.application.port.in.ISubscriptionUseCase;
import com.yowyob.tiibntick.core.gofp.application.port.out.ISubscriptionRepository;
import com.yowyob.tiibntick.core.gofp.domain.model.enums.SubscriptionStatus;
import com.yowyob.tiibntick.core.gofp.domain.model.enums.SubscriptionType;
import com.yowyob.tiibntick.core.gofp.domain.policy.CommissionPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionCoreService implements ISubscriptionUseCase {

    private final ISubscriptionRepository subscriptionRepository;

    @Override
    public Mono<SubscriptionEntity> subscribe(UUID freelancerActorId, SubscriptionType type, String paymentMethod) {
        return subscriptionRepository.findByFreelancerActorId(freelancerActorId)
            .defaultIfEmpty(SubscriptionEntity.builder()
                .id(UUID.randomUUID())
                .freelancerActorId(freelancerActorId)
                .createdAt(Instant.now())
                .build())
            .flatMap(sub -> {
                sub.setSubscriptionType(type.name());
                sub.setStatus(SubscriptionStatus.ACTIVE.name());
                sub.setStartDate(Instant.now());
                sub.setDeliveriesUsed(0);
                sub.setMonthlyQuota(type.isUnlimited() ? null : type.getMaxDeliveries());
                sub.setPaymentMethod(paymentMethod);
                sub.setUpdatedAt(Instant.now());
                // Reset date = 1er du mois suivant
                ZonedDateTime next = ZonedDateTime.now(ZoneOffset.UTC)
                    .withDayOfMonth(1).plusMonths(1).withHour(0).withMinute(0).withSecond(0);
                sub.setResetDate(next.toInstant());
                return subscriptionRepository.save(sub);
            });
    }

    @Override
    public Mono<SubscriptionEntity> cancel(UUID freelancerActorId) {
        return subscriptionRepository.findByFreelancerActorId(freelancerActorId)
            .flatMap(sub -> {
                sub.setStatus(SubscriptionStatus.CANCELLED.name());
                sub.setUpdatedAt(Instant.now());
                return subscriptionRepository.save(sub);
            });
    }

    @Override
    public Mono<SubscriptionEntity> resetMonthlyQuota(UUID freelancerActorId) {
        return subscriptionRepository.findByFreelancerActorId(freelancerActorId)
            .flatMap(sub -> {
                sub.setDeliveriesUsed(0);
                sub.setStatus(SubscriptionStatus.ACTIVE.name());
                ZonedDateTime next = ZonedDateTime.now(ZoneOffset.UTC)
                    .withDayOfMonth(1).plusMonths(1).withHour(0).withMinute(0).withSecond(0);
                sub.setResetDate(next.toInstant());
                sub.setUpdatedAt(Instant.now());
                return subscriptionRepository.save(sub);
            });
    }

    @Override
    public Mono<SubscriptionEntity> findByFreelancerActorId(UUID freelancerActorId) {
        return subscriptionRepository.findByFreelancerActorId(freelancerActorId);
    }

    @Override
    public Mono<Boolean> canAcceptDelivery(UUID freelancerActorId) {
        return subscriptionRepository.findByFreelancerActorId(freelancerActorId)
            .map(sub -> {
                if (!SubscriptionStatus.ACTIVE.name().equals(sub.getStatus())) return false;
                SubscriptionType plan = SubscriptionType.fromValue(sub.getSubscriptionType());
                return CommissionPolicy.canAcceptDelivery(plan, sub.getDeliveriesUsed());
            })
            .defaultIfEmpty(CommissionPolicy.canAcceptDelivery(SubscriptionType.FREE, 0));
    }
}
