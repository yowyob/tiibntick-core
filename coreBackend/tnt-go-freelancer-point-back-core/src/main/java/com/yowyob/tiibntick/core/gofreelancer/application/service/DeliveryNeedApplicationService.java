package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IDeliveryNeedRepository;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.DeliveryNeed;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.deliveryNeed.DeliveryNeedStatus;
import com.yowyob.tiibntick.core.gofreelancer.domain.port.in.AdminRelayPointUseCase;
import com.yowyob.tiibntick.core.gofreelancer.domain.port.in.DeliveryNeedUseCase;
import com.yowyob.tiibntick.core.gofreelancer.domain.port.out.PushNotificationPort;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.DeliveryNeedRequestDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.response.DeliveryNeedResponseDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.response.FreelancerCandidateDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Application service implementing the DeliveryNeedUseCase.
 * Handles delivery need lifecycle including assignment and relay point notifications.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryNeedApplicationService implements DeliveryNeedUseCase {

    private final IDeliveryNeedRepository deliveryNeedRepository;
    private final AdminRelayPointUseCase adminRelayPointUseCase;
    private final PushNotificationPort pushNotificationPort;
    private final com.yowyob.tiibntick.core.gofreelancer.application.usecase.MatchingUseCase matchingUseCase;

    @Override
    public Mono<DeliveryNeedResponseDTO> assignFreelancer(UUID deliveryNeedId, UUID freelancerId) {
        return deliveryNeedRepository.findById(deliveryNeedId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("DeliveryNeed not found: " + deliveryNeedId)))
                .flatMap(need -> {
                    // 1. Assign the freelancer and update status
                    need.setDeliveryId(freelancerId);
                    need.setStatus(DeliveryNeedStatus.ASSIGNED);

                    log.info("Assigning freelancer {} to DeliveryNeed {}", freelancerId, deliveryNeedId);

                    Mono<DeliveryNeed> saveNeed = deliveryNeedRepository.save(need);

                    // 2. If destination is a Relay Point -> Notify the relay point manager immediately
                    if (need.getTargetRelayPointId() != null) {
                        Mono<Void> notifyRelayPoint = adminRelayPointUseCase.getRelayPointDetails(need.getTargetRelayPointId())
                                .flatMap(relayPoint -> pushNotificationPort.sendPushNotification(
                                        relayPoint.getFreelancerId(),
                                        "Nouvelle Livraison Prévue !",
                                        "Un livreur vient d'accepter une course à destination de votre point relais. " +
                                                "Préparez-vous à recevoir le colis."
                                ))
                                .onErrorResume(e -> {
                                    log.error("Failed to notify relay point owner upon assignment", e);
                                    return Mono.empty();
                                });

                        return saveNeed.flatMap(saved -> notifyRelayPoint.thenReturn(saved));
                    }

                    return saveNeed;
                })
                .map(this::mapToResponse);
    }

    // --- Utility Mapper ---
    private DeliveryNeedResponseDTO mapToResponse(DeliveryNeed need) {
        // Basic mapping to avoid external dependencies that might not be fully migrated
        return com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.response.DeliveryNeedResponseDTO.builder()
                .id(need.getId())
                .userId(need.getUserId())
                .packetId(need.getPacketId())
                .pickupAddressId(need.getPickupAddressId())
                .deliveryAddressId(need.getDeliveryAddressId())
                .title(need.getTitle())
                .description(need.getDescription())
                .status(need.getStatus())
                .duration(need.getDuration())
                .signatureUrl(need.getSignatureUrl())
                .paymentMethod(need.getPaymentMethod())
                .transportMethod(need.getTransportMethod())
                .distance(need.getDistance())
                .deliveryId(need.getDeliveryId())
                .createdAt(need.getCreatedAt())
                .updatedAt(need.getUpdatedAt())
                .pickupDeadline(need.getPickupDeadline())
                .build();
    }

    // --- Other methods of the interface (Stubs pending full migration) ---

    @Override
    public Mono<DeliveryNeedResponseDTO> createDeliveryNeed(DeliveryNeedRequestDTO request) {
        return Mono.error(new UnsupportedOperationException("Not implemented yet"));
    }

    @Override
    public Flux<DeliveryNeedResponseDTO> getAllDeliveryNeeds() {
        return Flux.error(new UnsupportedOperationException("Not implemented yet"));
    }

    @Override
    public Mono<DeliveryNeedResponseDTO> getDeliveryNeed(UUID id) {
        return Mono.error(new UnsupportedOperationException("Not implemented yet"));
    }

    @Override
    public Flux<DeliveryNeedResponseDTO> getDeliveryNeedsByUserId(UUID userId) {
        return Flux.error(new UnsupportedOperationException("Not implemented yet"));
    }

    @Override
    public Mono<Void> deleteDeliveryNeed(UUID id) {
        return Mono.error(new UnsupportedOperationException("Not implemented yet"));
    }

    @Override
    public Flux<FreelancerCandidateDTO> getCandidatesWithPricing(UUID deliveryNeedId) {
        return deliveryNeedRepository.findById(deliveryNeedId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Delivery need not found: " + deliveryNeedId)))
                .flatMapMany(need -> {
                    // TODO: Récupérer les coordonnées depuis l'ID de l'adresse (via AddressRepository)
                    double pickupLat = 0.0; double pickupLon = 0.0;
                    double deliveryLat = 0.0; double deliveryLon = 0.0;

                    // TODO: Récupérer le volume en m³ à partir de need.getPacketId()
                    double packetVolumeM3 = 0.0;

                    return matchingUseCase.processMatchingForDeliveryNeed(
                            need.getId(), pickupLat, pickupLon, deliveryLat, deliveryLon,
                            packetVolumeM3, need.getPickupDeadline()
                    ).flatMapMany(Flux::fromIterable);
                })
                .map(candidate -> {
                    return FreelancerCandidateDTO.builder()
                            .freelancerId(candidate.getFreelancerId())
                            .firstName("À définir")
                            .lastName("À définir")
                            .rating(candidate.getRating())
                            
                            // TODO: Appeler le module Pricing pour avoir l'estimation
                            .estimatedPrice(0.0)
                            .priceBreakdown("Base: 0, Distance: 0")
                            .build();
                });
    }
}
