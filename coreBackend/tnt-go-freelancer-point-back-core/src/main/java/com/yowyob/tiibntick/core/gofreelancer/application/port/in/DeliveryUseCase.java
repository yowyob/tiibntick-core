package com.yowyob.tiibntick.core.gofreelancer.application.port.in;

import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.DeliveryAssistanceDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.DeliveryResponseDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.DeliveryStatusUpdateDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.DeliveryTrackingDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.DeliveryUpdateDTO;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.delivery.DeliveryStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface DeliveryUseCase {
    Mono<DeliveryResponseDTO> getDeliveryById(UUID id);
    Mono<DeliveryResponseDTO> getDeliveryByAnnouncementId(UUID announcementId);
    Mono<DeliveryResponseDTO> getDeliveryByDeliveryNeedId(UUID deliveryNeedId);
    Flux<DeliveryResponseDTO> getDeliveriesByFreelancerId(UUID freelancerId);
    Flux<DeliveryResponseDTO> getDeliveriesByStatus(DeliveryStatus status);
    Mono<DeliveryResponseDTO> updateDelivery(UUID id, DeliveryUpdateDTO dto);
    Mono<DeliveryResponseDTO> updateStatus(UUID id, DeliveryStatusUpdateDTO dto);
    Mono<DeliveryResponseDTO> cancelDelivery(UUID id);
    Mono<DeliveryTrackingDTO> trackDelivery(UUID announcementId);
    Flux<DeliveryTrackingDTO> trackDeliveryStream(UUID announcementId);
    Mono<DeliveryTrackingDTO> trackDeliveryByNeed(UUID deliveryNeedId, UUID callerId);
    Flux<DeliveryTrackingDTO> trackDeliveryByNeedStream(UUID deliveryNeedId, UUID callerId);
    /**
     * Checks ownership for a delivery-need without loading tracking data.
     * Used by the SSE controller to fail-fast before committing the 200 headers.
     *
     * @throws AccessDeniedException    if callerId is null or does not own the need
     * @throws IllegalArgumentException if the need does not exist (message contains "not found")
     */
    Mono<Void> checkTrackingOwnership(UUID deliveryNeedId, UUID callerId);
    Mono<DeliveryAssistanceDTO> getDeliveryAssistance(UUID id);
}
