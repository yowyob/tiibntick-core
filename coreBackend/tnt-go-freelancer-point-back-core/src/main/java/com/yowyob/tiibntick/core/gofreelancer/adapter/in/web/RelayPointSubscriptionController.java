package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web;

import com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser;
import com.yowyob.tiibntick.core.auth.domain.model.TntSecurityContext;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.RelayPointSubscriptionUseCase;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.RelayPointSubscriptionRequestDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.RelayPointSubscriptionStatusDTO;
import com.yowyob.tiibntick.core.gofreelancer.application.service.RelayPointAccessGuard;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Subscription of a relay point. Read and cancelled by the owner of the relay point or by staff.
 * Creating or renewing a subscription activates it for 30 days and sets its quota without taking any
 * payment, so it is reserved to staff.
 */
@RestController
@RequestMapping("/api/relay-points/{relayPointId}/subscription")
@RequiredArgsConstructor
public class RelayPointSubscriptionController {

    private final RelayPointSubscriptionUseCase subscriptionUseCase;
    private final RelayPointAccessGuard guard;

    @GetMapping
    public Mono<RelayPointSubscriptionStatusDTO> getStatus(@PathVariable UUID relayPointId,
            @CurrentUser(required = false) TntSecurityContext ctx) {
        return guard.requireOwnerOrStaff(relayPointId, ctx)
                .then(Mono.defer(() -> subscriptionUseCase.getSubscriptionStatus(relayPointId)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<RelayPointSubscriptionStatusDTO> createOrRenew(
            @PathVariable UUID relayPointId,
            @Valid @RequestBody RelayPointSubscriptionRequestDTO request,
            @CurrentUser(required = false) TntSecurityContext ctx) {
        return guard.requireStaff(ctx)
                .then(Mono.defer(() -> subscriptionUseCase.createOrRenew(relayPointId, request)));
    }

    @DeleteMapping
    public Mono<RelayPointSubscriptionStatusDTO> cancel(@PathVariable UUID relayPointId,
            @CurrentUser(required = false) TntSecurityContext ctx) {
        return guard.requireOwnerOrStaff(relayPointId, ctx)
                .then(Mono.defer(() -> subscriptionUseCase.cancel(relayPointId)));
    }

    @GetMapping("/eligible")
    public Mono<Boolean> isEligible(@PathVariable UUID relayPointId,
            @CurrentUser(required = false) TntSecurityContext ctx) {
        return guard.requireOwnerOrStaff(relayPointId, ctx)
                .then(Mono.defer(() -> subscriptionUseCase.isEligible(relayPointId)));
    }
}
