package com.yowyob.tiibntick.core.gofreelancer.domain.port.in;

import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.RelayPointSubscriptionRequestDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.RelayPointSubscriptionStatusDTO;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface RelayPointSubscriptionUseCase {
    Mono<RelayPointSubscriptionStatusDTO> getSubscriptionStatus(UUID relayPointId);
    Mono<RelayPointSubscriptionStatusDTO> createOrRenew(UUID relayPointId, RelayPointSubscriptionRequestDTO request);
    Mono<RelayPointSubscriptionStatusDTO> cancel(UUID relayPointId);
    /** Vérifie si un point relais a un abonnement ACTIVE et un quota restant. */
    Mono<Boolean> isEligible(UUID relayPointId);
}
