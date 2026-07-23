package com.yowyob.tiibntick.core.gofp.application.service;

import com.yowyob.tiibntick.core.gofp.adapter.out.persistence.entity.PaymentEntity;
import com.yowyob.tiibntick.core.gofp.adapter.out.persistence.entity.SubscriptionEntity;
import com.yowyob.tiibntick.core.gofp.application.port.in.ICommissionUseCase;
import com.yowyob.tiibntick.core.gofp.application.port.out.IDeliveryRepository;
import com.yowyob.tiibntick.core.gofp.application.port.out.IGofpEventPublisher;
import com.yowyob.tiibntick.core.gofp.application.port.out.IPaymentRepository;
import com.yowyob.tiibntick.core.gofp.application.port.out.ISubscriptionRepository;
import com.yowyob.tiibntick.core.gofp.domain.exception.DeliveryNotFoundException;
import com.yowyob.tiibntick.core.gofp.domain.model.CommissionBreakdown;
import com.yowyob.tiibntick.core.gofp.domain.model.enums.PaymentMethod;
import com.yowyob.tiibntick.core.gofp.domain.model.enums.PaymentStatus;
import com.yowyob.tiibntick.core.gofp.domain.model.enums.SubscriptionStatus;
import com.yowyob.tiibntick.core.gofp.domain.model.enums.SubscriptionType;
import com.yowyob.tiibntick.core.gofp.domain.policy.CommissionPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommissionCoreService implements ICommissionUseCase {

    private final IDeliveryRepository    deliveryRepository;
    private final ISubscriptionRepository subscriptionRepository;
    private final IPaymentRepository     paymentRepository;
    private final IGofpEventPublisher    eventPublisher;

    @Override
    // Chantier C · Audit n°3 · P5: payment/subscription saves and the outbox envelope
    // written by IGofpEventPublisher must commit atomically.
    @Transactional
    public Mono<PaymentEntity> processDeliveryCompletion(UUID deliveryId) {
        return deliveryRepository.findById(deliveryId)
            .switchIfEmpty(Mono.error(new DeliveryNotFoundException(deliveryId)))
            .flatMap(delivery -> {
                double gross = delivery.getTarif() != null ? delivery.getTarif() : 0.0;
                UUID freelancerId = delivery.getFreelancerActorId();

                return subscriptionRepository.findByFreelancerActorId(freelancerId)
                    .defaultIfEmpty(defaultSubscription(freelancerId))
                    .flatMap(sub -> {
                        SubscriptionType plan = SubscriptionType.fromValue(sub.getSubscriptionType());

                        // Auto-reset quota mensuel si date dépassée
                        sub = autoResetIfNeeded(sub);

                        CommissionBreakdown breakdown = CommissionPolicy.compute(gross, plan, "FCFA");

                        log.info("[COMMISSION] deliveryId={} plan={} gross={} FCFA commission={}% ({}) net={}",
                            deliveryId, plan, gross,
                            breakdown.getCommissionPercent(),
                            breakdown.getCommissionAmount(),
                            breakdown.getNetAmount());

                        // Persiste le paiement
                        PaymentEntity payment = PaymentEntity.builder()
                            .id(UUID.randomUUID())
                            .deliveryId(deliveryId)
                            .freelancerActorId(freelancerId)
                            .clientActorId(delivery.getAnnouncementId()) // résolution via annonce si besoin
                            .grossAmount(breakdown.getGrossAmount())
                            .commissionAmount(breakdown.getCommissionAmount())
                            .netAmount(breakdown.getNetAmount())
                            .commissionPercent(breakdown.getCommissionPercent())
                            .subscriptionType(plan.name())
                            .paymentMethod(PaymentMethod.MOBILE_MONEY.name())
                            .status(PaymentStatus.PAID.name())
                            .paidAt(Instant.now())
                            .createdAt(Instant.now())
                            .updatedAt(Instant.now())
                            .build();

                        // Met à jour le quota
                        sub.setDeliveriesUsed(sub.getDeliveriesUsed() + 1);
                        SubscriptionEntity finalSub = sub;

                        // Vérifie si quota épuisé → suspend
                        boolean quotaExhausted = !plan.hasRemainingQuota(finalSub.getDeliveriesUsed());

                        Mono<Void> suspendIfNeeded = Mono.empty();
                        if (quotaExhausted) {
                            finalSub.setStatus(SubscriptionStatus.SUSPENDED.name());
                            suspendIfNeeded = eventPublisher.publishSubscriptionSuspended(
                                finalSub.getId(), freelancerId);
                        }

                        SubscriptionEntity toSave = finalSub;
                        Mono<Void> finalSuspendIfNeeded = suspendIfNeeded;
                        return paymentRepository.save(payment)
                            .flatMap(saved -> subscriptionRepository.save(toSave)
                                .then(finalSuspendIfNeeded)
                                .thenReturn(saved));
                    });
            });
    }

    @Override
    public Mono<CommissionBreakdown> previewCommission(UUID freelancerActorId, double grossAmount) {
        return subscriptionRepository.findByFreelancerActorId(freelancerActorId)
            .defaultIfEmpty(defaultSubscription(freelancerActorId))
            .map(sub -> {
                SubscriptionType plan = SubscriptionType.fromValue(sub.getSubscriptionType());
                return CommissionPolicy.compute(grossAmount, plan, "FCFA");
            });
    }

    @Override
    public Mono<PaymentEntity> findPaymentByDeliveryId(UUID deliveryId) {
        return paymentRepository.findByDeliveryId(deliveryId);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private SubscriptionEntity defaultSubscription(UUID freelancerActorId) {
        return SubscriptionEntity.builder()
            .id(UUID.randomUUID())
            .freelancerActorId(freelancerActorId)
            .subscriptionType(SubscriptionType.FREE.name())
            .status(SubscriptionStatus.ACTIVE.name())
            .deliveriesUsed(0)
            .monthlyQuota(SubscriptionType.FREE.getMaxDeliveries())
            .startDate(Instant.now())
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
    }

    private SubscriptionEntity autoResetIfNeeded(SubscriptionEntity sub) {
        if (sub.getResetDate() != null && Instant.now().isAfter(sub.getResetDate())) {
            sub.setDeliveriesUsed(0);
            sub.setStatus(SubscriptionStatus.ACTIVE.name());
            // Prochaine date de reset : 1er du mois suivant
            ZonedDateTime next = ZonedDateTime.now(ZoneOffset.UTC)
                .withDayOfMonth(1).plusMonths(1).withHour(0).withMinute(0).withSecond(0);
            sub.setResetDate(next.toInstant());
        }
        return sub;
    }
}
