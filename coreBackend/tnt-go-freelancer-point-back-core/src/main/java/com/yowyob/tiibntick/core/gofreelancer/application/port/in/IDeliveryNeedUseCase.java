package com.yowyob.tiibntick.core.gofreelancer.application.port.in;

import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.DeliveryNeedRequestDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.response.DeliveryNeedResponseDTO;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Inbound port — DeliveryNeed use cases exposed by Layer 6 to the BFF.
 */
public interface IDeliveryNeedUseCase {

    Mono<DeliveryNeedResponseDTO> createDeliveryNeed(DeliveryNeedRequestDTO request);

    Mono<DeliveryNeedResponseDTO> getDeliveryNeedById(UUID id);

    Flux<DeliveryNeedResponseDTO> getDeliveryNeedsByUserId(UUID userId);

    Mono<DeliveryNeedResponseDTO> updateDeliveryNeed(UUID id, DeliveryNeedRequestDTO request);

    Mono<Void> deleteDeliveryNeed(UUID id);

    Mono<DeliveryNeedResponseDTO> promoteToAnnouncement(UUID deliveryNeedId);
}
