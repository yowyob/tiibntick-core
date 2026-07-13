package com.yowyob.tiibntick.core.gofp.application.service;

import com.yowyob.tiibntick.core.gofp.adapter.out.persistence.entity.RelayHubSubscriptionEntity;
import com.yowyob.tiibntick.core.gofp.application.port.in.IRelayHubSubscriptionUseCase;
import com.yowyob.tiibntick.core.gofp.application.port.out.IRelayHubSubscriptionRepository;
import com.yowyob.tiibntick.core.gofp.domain.model.enums.RelayHubSubscriptionType;
import com.yowyob.tiibntick.core.gofp.domain.model.enums.SubscriptionStatus;
import com.yowyob.tiibntick.core.gofp.domain.policy.RelayHubSubscriptionPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * Service applicatif — Abonnements des points relais.
 *
 * Logique métier :
 *   - subscribe    : upsert du plan, réinitialise le compteur si downgrade vers FREE
 *   - cancel       : passage au statut CANCELLED
 *   - canAcceptPacket : vérifie statut ACTIVE + capacité restante
 *   - increment/decrement : gestion en temps réel du compteur de colis
 *
 * @author TiiBnTickTeam
 * @date 10/07/2026
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RelayHubSubscriptionCoreService implements IRelayHubSubscriptionUseCase {

    private final IRelayHubSubscriptionRepository subscriptionRepository;

    // ── Souscription ──────────────────────────────────────────────────────────

    @Override
    public Mono<RelayHubSubscriptionEntity> subscribe(UUID relayHubId,
                                                       RelayHubSubscriptionType type,
                                                       String paymentMethod) {
        return subscriptionRepository.findByRelayHubId(relayHubId)
            .defaultIfEmpty(RelayHubSubscriptionEntity.builder()
                .id(UUID.randomUUID())
                .relayHubId(relayHubId)
                .packetsUsed(0)
                .createdAt(Instant.now())
                .build())
            .flatMap(sub -> {
                sub.setSubscriptionType(type.name());
                sub.setStatus(SubscriptionStatus.ACTIVE.name());
                sub.setStartDate(Instant.now());
                sub.setPrice(RelayHubSubscriptionPolicy.monthlyPrice(type));
                sub.setPaymentMethod(paymentMethod);
                sub.setMaxPacketsSimultaneous(type.isUnlimited() ? null : type.getMaxPacketsSimultaneous());
                sub.setCommissionPercent(type.getCommissionPercent());
                sub.setUpdatedAt(Instant.now());
                // On conserve packetsUsed existant — les colis déjà en stock ne disparaissent pas
                if (sub.getPacketsUsed() == null) sub.setPacketsUsed(0);
                log.info("Souscription plan {} pour relay hub {}", type.name(), relayHubId);
                return subscriptionRepository.save(sub);
            });
    }

    // ── Annulation ────────────────────────────────────────────────────────────

    @Override
    public Mono<RelayHubSubscriptionEntity> cancel(UUID relayHubId) {
        return subscriptionRepository.findByRelayHubId(relayHubId)
            .flatMap(sub -> {
                sub.setStatus(SubscriptionStatus.CANCELLED.name());
                sub.setUpdatedAt(Instant.now());
                log.info("Annulation abonnement relay hub {}", relayHubId);
                return subscriptionRepository.save(sub);
            });
    }

    // ── Lecture ───────────────────────────────────────────────────────────────

    @Override
    public Mono<RelayHubSubscriptionEntity> findByRelayHubId(UUID relayHubId) {
        return subscriptionRepository.findByRelayHubId(relayHubId);
    }

    // ── Vérification capacité ─────────────────────────────────────────────────

    @Override
    public Mono<Boolean> canAcceptPacket(UUID relayHubId) {
        return subscriptionRepository.findByRelayHubId(relayHubId)
            .map(sub -> {
                if (!SubscriptionStatus.ACTIVE.name().equals(sub.getStatus())) return false;
                RelayHubSubscriptionType plan =
                    RelayHubSubscriptionType.fromValue(sub.getSubscriptionType());
                int used = sub.getPacketsUsed() != null ? sub.getPacketsUsed() : 0;
                return RelayHubSubscriptionPolicy.canAcceptPacket(plan, used);
            })
            // Sans abonnement → FREE par défaut (5 colis max, considéré vide)
            .defaultIfEmpty(RelayHubSubscriptionPolicy.canAcceptPacket(RelayHubSubscriptionType.FREE, 0));
    }

    // ── Gestion du compteur de colis ─────────────────────────────────────────

    @Override
    public Mono<RelayHubSubscriptionEntity> incrementPacketsUsed(UUID relayHubId) {
        return subscriptionRepository.findByRelayHubId(relayHubId)
            .flatMap(sub -> {
                int current = sub.getPacketsUsed() != null ? sub.getPacketsUsed() : 0;
                sub.setPacketsUsed(current + 1);
                sub.setUpdatedAt(Instant.now());
                return subscriptionRepository.save(sub);
            });
    }

    @Override
    public Mono<RelayHubSubscriptionEntity> decrementPacketsUsed(UUID relayHubId) {
        return subscriptionRepository.findByRelayHubId(relayHubId)
            .flatMap(sub -> {
                int current = sub.getPacketsUsed() != null ? sub.getPacketsUsed() : 0;
                sub.setPacketsUsed(Math.max(0, current - 1));
                sub.setUpdatedAt(Instant.now());
                return subscriptionRepository.save(sub);
            });
    }
}
