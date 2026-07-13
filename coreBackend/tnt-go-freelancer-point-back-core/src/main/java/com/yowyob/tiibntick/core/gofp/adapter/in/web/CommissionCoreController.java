package com.yowyob.tiibntick.core.gofp.adapter.in.web;

import com.yowyob.tiibntick.core.gofp.adapter.out.persistence.entity.PaymentEntity;
import com.yowyob.tiibntick.core.gofp.application.port.in.ICommissionUseCase;
import com.yowyob.tiibntick.core.gofp.domain.model.CommissionBreakdown;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Tag(name = "GOFP — Commissions", description = "API métier générique — Commissions et paiements")
@RestController
@RequestMapping("/api/v1/gofp/commissions")
@RequiredArgsConstructor
public class CommissionCoreController {

    private final ICommissionUseCase commissionUseCase;

    @Operation(summary = "Traiter la commission d'une livraison complétée")
    @PostMapping("/delivery/{deliveryId}/process")
    public Mono<PaymentEntity> process(@PathVariable UUID deliveryId) {
        return commissionUseCase.processDeliveryCompletion(deliveryId);
    }

    @Operation(summary = "Aperçu de la commission (sans persistance)")
    @GetMapping("/preview")
    public Mono<CommissionBreakdown> preview(@RequestParam UUID freelancerActorId,
                                              @RequestParam double grossAmount) {
        return commissionUseCase.previewCommission(freelancerActorId, grossAmount);
    }

    @Operation(summary = "Récupérer le paiement d'une livraison")
    @GetMapping("/delivery/{deliveryId}/payment")
    public Mono<PaymentEntity> getPayment(@PathVariable UUID deliveryId) {
        return commissionUseCase.findPaymentByDeliveryId(deliveryId);
    }
}
