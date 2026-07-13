package com.yowyob.tiibntick.core.gofp.adapter.in.web;

import com.yowyob.tiibntick.core.gofp.adapter.out.persistence.entity.SubscriptionEntity;
import com.yowyob.tiibntick.core.gofp.application.port.in.ISubscriptionUseCase;
import com.yowyob.tiibntick.core.gofp.domain.model.enums.SubscriptionType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Tag(name = "GOFP — Abonnements", description = "API métier générique — Plans d'abonnement livreurs")
@RestController
@RequestMapping("/api/v1/gofp/subscriptions")
@RequiredArgsConstructor
public class SubscriptionCoreController {

    private final ISubscriptionUseCase subscriptionUseCase;

    @Operation(summary = "Souscrire ou changer de plan")
    @PostMapping("/freelancer/{freelancerActorId}")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<SubscriptionEntity> subscribe(@PathVariable UUID freelancerActorId,
                                               @RequestParam String subscriptionType,
                                               @RequestParam(required = false) String paymentMethod) {
        return subscriptionUseCase.subscribe(freelancerActorId,
            SubscriptionType.fromValue(subscriptionType), paymentMethod);
    }

    @GetMapping("/freelancer/{freelancerActorId}")
    public Mono<SubscriptionEntity> find(@PathVariable UUID freelancerActorId) {
        return subscriptionUseCase.findByFreelancerActorId(freelancerActorId);
    }

    @GetMapping("/freelancer/{freelancerActorId}/can-accept")
    public Mono<Boolean> canAccept(@PathVariable UUID freelancerActorId) {
        return subscriptionUseCase.canAcceptDelivery(freelancerActorId);
    }

    @PatchMapping("/freelancer/{freelancerActorId}/cancel")
    public Mono<SubscriptionEntity> cancel(@PathVariable UUID freelancerActorId) {
        return subscriptionUseCase.cancel(freelancerActorId);
    }

    @PatchMapping("/freelancer/{freelancerActorId}/reset-quota")
    public Mono<SubscriptionEntity> resetQuota(@PathVariable UUID freelancerActorId) {
        return subscriptionUseCase.resetMonthlyQuota(freelancerActorId);
    }
}
