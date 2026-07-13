package com.yowyob.tiibntick.core.gofp.application.port.in;

import com.yowyob.tiibntick.core.gofp.adapter.out.persistence.entity.PaymentEntity;
import com.yowyob.tiibntick.core.gofp.domain.model.CommissionBreakdown;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ICommissionUseCase {

    /**
     * Traite la commission lors de la complétion d'une livraison.
     * Calcule le breakdown, persiste le paiement, met à jour le quota.
     */
    Mono<PaymentEntity> processDeliveryCompletion(UUID deliveryId);

    /**
     * Calcule (sans persister) le breakdown de commission pour un montant donné.
     */
    Mono<CommissionBreakdown> previewCommission(UUID freelancerActorId, double grossAmount);

    Mono<PaymentEntity> findPaymentByDeliveryId(UUID deliveryId);
}
