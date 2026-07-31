package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.AddressDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.DeliveryNeedRequestDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.response.DeliveryNeedResponseDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.response.FreelancerCandidateDTO;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IDeliveryNeedRepository;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.DeliveryNeed;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.deliveryNeed.DeliveryNeedStatus;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.AddressUseCase;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.AdminRelayPointUseCase;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.DeliveryNeedUseCase;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpUserRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.PushNotificationPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * Application service implementing the DeliveryNeedUseCase.
 * Handles delivery need lifecycle including assignment and relay point notifications.
 *
 * @author MANFOUO BRAUN
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryNeedApplicationService implements DeliveryNeedUseCase {

    private final IDeliveryNeedRepository deliveryNeedRepository;
    private final AdminRelayPointUseCase adminRelayPointUseCase;
    private final PushNotificationPort pushNotificationPort;
    private final com.yowyob.tiibntick.core.gofreelancer.application.usecase.MatchingUseCase matchingUseCase;
    private final AddressUseCase addressUseCase;
    private final GofpUserRepository gofpUserRepository;
    private final DatabaseClient databaseClient;

    @Override
    public Mono<DeliveryNeedResponseDTO> createDeliveryNeed(DeliveryNeedRequestDTO request) {
        if (request == null) {
            return Mono.error(new IllegalArgumentException("Delivery need request is required"));
        }
        if (request.getUserId() == null) {
            return Mono.error(new IllegalArgumentException("userId is required"));
        }
        if (request.getTitle() == null || request.getTitle().isBlank()) {
            return Mono.error(new IllegalArgumentException("title is required"));
        }
        if (request.getPickupAddress() == null || request.getPickupAddress().getAddress() == null) {
            return Mono.error(new IllegalArgumentException("pickupAddress is required"));
        }
        if (request.getDeliveryAddress() == null || request.getDeliveryAddress().getAddress() == null) {
            return Mono.error(new IllegalArgumentException("deliveryAddress is required"));
        }

        Mono<AddressDTO> pickupMono = addressUseCase.createAddress(request.getPickupAddress());
        Mono<AddressDTO> deliveryMono = addressUseCase.createAddress(request.getDeliveryAddress());

        return ensureLegacyUser(request.getUserId())
                .then(Mono.zip(pickupMono, deliveryMono))
                .flatMap(addresses -> {
                    Instant now = Instant.now();
                    DeliveryNeed need = DeliveryNeed.builder()
                            .id(UUID.randomUUID())
                            .userId(request.getUserId())
                            .title(request.getTitle().trim())
                            .description(request.getDescription())
                            .status(DeliveryNeedStatus.PENDING)
                            .pickupAddressId(addresses.getT1().getId())
                            .deliveryAddressId(addresses.getT2().getId())
                            .signatureUrl(request.getSignatureUrl())
                            .paymentMethod(request.getPaymentMethod())
                            .transportMethod(request.getTransportMethod())
                            .distance(request.getDistance())
                            .duration(request.getDuration())
                            .pickupDeadline(request.getPickupDeadline())
                            .targetRelayPointId(request.getTargetRelayPointId())
                            .requestedStorageDays(request.getRequestedStorageDays())
                            .createdAt(now)
                            .updatedAt(now)
                            .build();
                    log.info("Creating DeliveryNeed {} for user {}", need.getId(), need.getUserId());
                    return deliveryNeedRepository.save(need);
                })
                .map(this::mapToResponse);
    }

    /**
     * {@code delivery_needs.user_id} FKs {@code users(id)}. Ensure a legacy row exists
     * (from GofpUser when available, otherwise a placeholder).
     *
     * @author MANFOUO BRAUN
     */
    private Mono<Void> ensureLegacyUser(UUID userId) {
        return databaseClient.sql("SELECT 1 FROM users WHERE id = :id")
                .bind("id", userId)
                .map((row, meta) -> 1)
                .first()
                .flatMap(exists -> Mono.<Void>empty())
                .switchIfEmpty(Mono.defer(() ->
                        gofpUserRepository.findByCoreUserId(userId)
                                .defaultIfEmpty(com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpUser.builder()
                                        .firstName("User")
                                        .lastName(userId.toString().substring(0, 8))
                                        .build())
                                .flatMap(u -> databaseClient.sql("""
                                                INSERT INTO users (id, first_name, last_name, password)
                                                VALUES (:id, :fn, :ln, :pw)
                                                ON CONFLICT (id) DO NOTHING
                                                """)
                                        .bind("id", userId)
                                        .bind("fn", u.getFirstName() != null ? u.getFirstName() : "User")
                                        .bind("ln", u.getLastName() != null ? u.getLastName() : "GOFP")
                                        .bind("pw", "{noop}placeholder")
                                        .fetch()
                                        .rowsUpdated()
                                        .then())
                ));
    }

    @Override
    public Flux<DeliveryNeedResponseDTO> getAllDeliveryNeeds() {
        return deliveryNeedRepository.findAll().map(this::mapToResponse);
    }

    @Override
    public Mono<DeliveryNeedResponseDTO> getDeliveryNeed(UUID id) {
        return deliveryNeedRepository.findById(id)
                .map(this::mapToResponse)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("DeliveryNeed not found: " + id)));
    }

    @Override
    public Flux<DeliveryNeedResponseDTO> getDeliveryNeedsByUserId(UUID userId) {
        return deliveryNeedRepository.findAllByUserId(userId).map(this::mapToResponse);
    }

    @Override
    public Mono<Void> deleteDeliveryNeed(UUID id) {
        return deliveryNeedRepository.findById(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("DeliveryNeed not found: " + id)))
                .flatMap(need -> deliveryNeedRepository.deleteById(id));
    }

    @Override
    public Mono<DeliveryNeedResponseDTO> assignFreelancer(UUID deliveryNeedId, UUID freelancerId) {
        return deliveryNeedRepository.findById(deliveryNeedId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("DeliveryNeed not found: " + deliveryNeedId)))
                .flatMap(need -> {
                    need.setDeliveryId(freelancerId);
                    need.setStatus(DeliveryNeedStatus.ASSIGNED);
                    need.setUpdatedAt(Instant.now());

                    log.info("Assigning freelancer {} to DeliveryNeed {}", freelancerId, deliveryNeedId);

                    Mono<DeliveryNeed> saveNeed = deliveryNeedRepository.save(need);

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

    @Override
    public Flux<FreelancerCandidateDTO> getCandidatesWithPricing(UUID deliveryNeedId) {
        return deliveryNeedRepository.findById(deliveryNeedId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Delivery need not found: " + deliveryNeedId)))
                .flatMapMany(need -> {
                    double pickupLat = 0.0;
                    double pickupLon = 0.0;
                    double deliveryLat = 0.0;
                    double deliveryLon = 0.0;
                    double packetVolumeM3 = 0.0;

                    return matchingUseCase.processMatchingForDeliveryNeed(
                            need.getId(), pickupLat, pickupLon, deliveryLat, deliveryLon,
                            packetVolumeM3, need.getPickupDeadline()
                    ).flatMapMany(Flux::fromIterable);
                })
                .map(candidate -> FreelancerCandidateDTO.builder()
                        .freelancerId(candidate.getFreelancerId())
                        .firstName("À définir")
                        .lastName("À définir")
                        .rating(candidate.getRating())
                        .estimatedPrice(0.0)
                        .priceBreakdown("Base: 0, Distance: 0")
                        .build());
    }

    private DeliveryNeedResponseDTO mapToResponse(DeliveryNeed need) {
        return DeliveryNeedResponseDTO.builder()
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
}
