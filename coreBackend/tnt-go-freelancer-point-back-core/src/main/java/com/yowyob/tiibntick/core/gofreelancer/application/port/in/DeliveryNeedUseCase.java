package com.yowyob.tiibntick.core.gofreelancer.application.port.in;

import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.DeliveryNeedRequestDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.response.DeliveryNeedResponseDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.response.FreelancerCandidateDTO;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface DeliveryNeedUseCase {
    Mono<DeliveryNeedResponseDTO> createDeliveryNeed(DeliveryNeedRequestDTO request);
    Flux<DeliveryNeedResponseDTO> getAllDeliveryNeeds();
    Mono<DeliveryNeedResponseDTO> getDeliveryNeed(UUID id);
    Flux<DeliveryNeedResponseDTO> getDeliveryNeedsByUserId(UUID userId);
    Mono<Void> deleteDeliveryNeed(UUID id);
    Flux<FreelancerCandidateDTO> getCandidatesWithPricing(UUID deliveryNeedId);
    Mono<DeliveryNeedResponseDTO> assignFreelancer(UUID deliveryNeedId, UUID freelancerId);
}
