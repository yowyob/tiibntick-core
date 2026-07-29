package com.yowyob.tiibntick.core.gofreelancer.domain.port.in;

import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.SubscriptionStatusResponseDTO;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Inbound port for subscription-related use cases.
 *
 * @author TiiBnTickTeam
 * @date 08/07/2026
 */
public interface SubscriptionUseCase {

    /**
     * Returns the full subscription state of a delivery person:
     * plan type, quota usage, commission rate, validity dates.
     *
     * @param freelancerId the UUID of the delivery person
     * @return the subscription status, or 404 if no subscription exists
     */
    Mono<SubscriptionStatusResponseDTO> getSubscriptionStatus(UUID freelancerId);
    Mono<SubscriptionStatusResponseDTO> updateSubscriptionPrice(UUID freelancerId, Float newPrice);
}
