package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web;

import com.yowyob.tiibntick.core.gofreelancer.application.port.in.RelayPointSubscriptionUseCase;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.RelayPointSubscriptionRequestDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.RelayPointSubscriptionStatusDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/api/relay-points/{relayPointId}/subscription")
@RequiredArgsConstructor
public class RelayPointSubscriptionController {

    private final RelayPointSubscriptionUseCase subscriptionUseCase;

    @GetMapping
    public Mono<RelayPointSubscriptionStatusDTO> getStatus(@PathVariable UUID relayPointId) {
        return subscriptionUseCase.getSubscriptionStatus(relayPointId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<RelayPointSubscriptionStatusDTO> createOrRenew(
            @PathVariable UUID relayPointId,
            @Valid @RequestBody RelayPointSubscriptionRequestDTO request) {
        return subscriptionUseCase.createOrRenew(relayPointId, request);
    }

    @DeleteMapping
    public Mono<RelayPointSubscriptionStatusDTO> cancel(@PathVariable UUID relayPointId) {
        return subscriptionUseCase.cancel(relayPointId);
    }

    @GetMapping("/eligible")
    public Mono<Boolean> isEligible(@PathVariable UUID relayPointId) {
        return subscriptionUseCase.isEligible(relayPointId);
    }
}
