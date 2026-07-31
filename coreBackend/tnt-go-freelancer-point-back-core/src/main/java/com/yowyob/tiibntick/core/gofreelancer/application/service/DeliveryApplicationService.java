package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.realtime.application.port.in.IGetPresenceUseCase;
import com.yowyob.tiibntick.core.realtime.domain.model.PresenceRecord;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.DeliveryAssistanceDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.DeliveryResponseDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.DeliveryStatusUpdateDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.DeliveryTrackingDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.DeliveryUpdateDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.entity.AddressEntity;
import com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository.AddressReactiveRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IAnnouncementRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IDeliveryNeedRepository;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.Announcement;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.Delivery;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.DeliveryNeed;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.delivery.DeliveryStatus;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.DeliveryUseCase;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.CachePort;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.DeliveryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.UUID;

/**
 * Application service implementing DeliveryUseCase.
 *
 * <p>Handles delivery queries, updates, and tracking with state-aware logic:
 * <ul>
 *   <li><strong>CREATED</strong> — pickup location + freelancer GPS → route to pickup</li>
 *   <li><strong>PICKED_UP / IN_TRANSIT</strong> — freelancer GPS → route to delivery destination</li>
 *   <li><strong>Terminal states</strong> — last known positions, no live update</li>
 * </ul>
 *
 * @author François-Charles ATANGA
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryApplicationService implements DeliveryUseCase {

    private final DeliveryRepository deliveryRepository;
    private final IDeliveryNeedRepository deliveryNeedRepository;
    private final IAnnouncementRepository announcementRepository;
    private final AddressReactiveRepository addressRepository;
    private final IGetPresenceUseCase getPresenceUseCase;
    private final CachePort cachePort;

    private static final String TENANT_ID_DEFAULT = "00000000-0000-0000-0000-000000000001";
    private static final Duration TRACKING_CACHE_TTL = Duration.ofSeconds(5);

    // ══════════════════════════════════════════════════════════════════════
    // Queries
    // ══════════════════════════════════════════════════════════════════════

    @Override
    public Mono<DeliveryResponseDTO> getDeliveryById(UUID id) {
        return deliveryRepository.findById(id)
                .map(this::toResponseDTO)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Delivery not found: " + id)));
    }

    @Override
    public Mono<DeliveryResponseDTO> getDeliveryByAnnouncementId(UUID announcementId) {
        return deliveryRepository.findByAnnouncementId(announcementId)
                .map(this::toResponseDTO)
                .switchIfEmpty(Mono.error(new IllegalArgumentException(
                        "Delivery not found for announcement: " + announcementId)));
    }

    @Override
    public Mono<DeliveryResponseDTO> getDeliveryByDeliveryNeedId(UUID deliveryNeedId) {
        return deliveryRepository.findByDeliveryNeedId(deliveryNeedId)
                .map(this::toResponseDTO)
                .switchIfEmpty(Mono.error(new IllegalArgumentException(
                        "Delivery not found for delivery need: " + deliveryNeedId)));
    }

    @Override
    public Flux<DeliveryResponseDTO> getDeliveriesByFreelancerId(UUID freelancerId) {
        return deliveryRepository.findAllByFreelancerId(freelancerId)
                .map(this::toResponseDTO);
    }

    @Override
    public Flux<DeliveryResponseDTO> getDeliveriesByStatus(DeliveryStatus status) {
        return deliveryRepository.findAllByStatus(status)
                .map(this::toResponseDTO);
    }

    // ══════════════════════════════════════════════════════════════════════
    // Mutations
    // ══════════════════════════════════════════════════════════════════════

    @Override
    public Mono<DeliveryResponseDTO> updateDelivery(UUID id, DeliveryUpdateDTO dto) {
        return deliveryRepository.findById(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Delivery not found: " + id)))
                .flatMap(delivery -> {
                    if (dto.getTarif() != null)           delivery.setTarif(dto.getTarif());
                    if (dto.getPickupMinTime() != null)   delivery.setPickupMinTime(dto.getPickupMinTime());
                    if (dto.getPickupMaxTime() != null)   delivery.setPickupMaxTime(dto.getPickupMaxTime());
                    if (dto.getDeliveryMinTime() != null) delivery.setDeliveryMinTime(dto.getDeliveryMinTime());
                    if (dto.getDeliveryMaxTime() != null) delivery.setDeliveryMaxTime(dto.getDeliveryMaxTime());
                    return deliveryRepository.save(delivery);
                })
                .map(this::toResponseDTO);
    }

    @Override
    public Mono<DeliveryResponseDTO> updateStatus(UUID id, DeliveryStatusUpdateDTO dto) {
        // Status transitions are handled by DeliveryStatusApplicationService
        // (blockchain anchoring, payment, notifications — too complex for this service)
        return Mono.error(new UnsupportedOperationException(
                "Use DeliveryStatusApplicationService.updateStatus() for status transitions"));
    }

    @Override
    public Mono<DeliveryResponseDTO> cancelDelivery(UUID id) {
        return deliveryRepository.findById(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Delivery not found: " + id)))
                .flatMap(delivery -> {
                    DeliveryStatus current = delivery.getStatus();
                    if (current == DeliveryStatus.DELIVERED
                            || current == DeliveryStatus.FAILED
                            || current == DeliveryStatus.CANCELLED) {
                        return Mono.error(new IllegalStateException(
                                "Cannot cancel delivery in terminal state: " + current));
                    }
                    delivery.setStatus(DeliveryStatus.CANCELLED);
                    return deliveryRepository.save(delivery);
                })
                .map(this::toResponseDTO);
    }

    // ══════════════════════════════════════════════════════════════════════
    // Tracking — state-aware
    // ══════════════════════════════════════════════════════════════════════

    @Override
    public Mono<DeliveryTrackingDTO> trackDelivery(UUID announcementId) {
        String cacheKey = "tracking:announcement:" + announcementId;
        return cachePort.get(cacheKey, DeliveryTrackingDTO.class)
                .switchIfEmpty(buildTrackingDTOByAnnouncementId(announcementId)
                        .flatMap(dto -> cachePort.set(cacheKey, dto, TRACKING_CACHE_TTL).thenReturn(dto)));
    }

    @Override
    public Flux<DeliveryTrackingDTO> trackDeliveryStream(UUID announcementId) {
        return Flux.interval(Duration.ofSeconds(5))
                .flatMap(tick -> buildTrackingDTOByAnnouncementId(announcementId)
                        .onErrorResume(e -> {
                            log.warn("Tracking error for announcement {}: {}", announcementId, e.getMessage());
                            return Mono.empty();
                        }))
                .distinctUntilChanged();
    }

    @Override
    public Mono<DeliveryTrackingDTO> trackDeliveryByNeed(UUID deliveryNeedId) {
        String cacheKey = "tracking:need:" + deliveryNeedId;
        return cachePort.get(cacheKey, DeliveryTrackingDTO.class)
                .switchIfEmpty(buildTrackingDTOByDeliveryNeedId(deliveryNeedId)
                        .flatMap(dto -> cachePort.set(cacheKey, dto, TRACKING_CACHE_TTL).thenReturn(dto)));
    }

    @Override
    public Flux<DeliveryTrackingDTO> trackDeliveryByNeedStream(UUID deliveryNeedId) {
        return Flux.interval(Duration.ofSeconds(5))
                .flatMap(tick -> buildTrackingDTOByDeliveryNeedId(deliveryNeedId)
                        .onErrorResume(e -> {
                            log.warn("Tracking error for delivery need {}: {}", deliveryNeedId, e.getMessage());
                            return Mono.empty();
                        }))
                .distinctUntilChanged();
    }

    /**
     * Builds navigation assistance for the courier based on delivery status and addresses.
     * Presence lookup failures are ignored (no-op notify / empty GPS).
     *
     * @author MANFOUO BRAUN
     */
    @Override
    public Mono<DeliveryAssistanceDTO> getDeliveryAssistance(UUID id) {
        return deliveryRepository.findById(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Delivery not found: " + id)))
                .flatMap(delivery -> {
                    if (delivery.getDeliveryNeedId() == null) {
                        return Mono.just(DeliveryAssistanceDTO.builder()
                                .deliveryId(delivery.getId())
                                .currentStatus(delivery.getStatus())
                                .stepDescription(stepDescription(delivery.getStatus()))
                                .build());
                    }
                    return deliveryNeedRepository.findById(delivery.getDeliveryNeedId())
                            .switchIfEmpty(Mono.error(new IllegalArgumentException(
                                    "DeliveryNeed not found: " + delivery.getDeliveryNeedId())))
                            .flatMap(need -> {
                                Mono<AddressEntity> pickup = addressRepository.findById(need.getPickupAddressId())
                                        .defaultIfEmpty(new AddressEntity());
                                Mono<AddressEntity> dest = addressRepository.findById(need.getDeliveryAddressId())
                                        .defaultIfEmpty(new AddressEntity());
                                Mono<PresenceRecord> presence = delivery.getFreelancerId() != null
                                        ? getPresenceUseCase.getPresence(
                                                        delivery.getFreelancerId().toString(), TENANT_ID_DEFAULT)
                                                .onErrorResume(e -> {
                                                    log.warn("Assistance presence lookup failed for {}: {}",
                                                            delivery.getFreelancerId(), e.getMessage());
                                                    return Mono.empty();
                                                })
                                        : Mono.empty();

                                return Mono.zip(pickup, dest)
                                        .flatMap(tuple -> {
                                            AddressEntity pickupAddr = tuple.getT1();
                                            AddressEntity deliveryAddr = tuple.getT2();
                                            boolean toPickup = delivery.getStatus() == DeliveryStatus.CREATED;

                                            Double targetLat = toPickup
                                                    ? pickupAddr.getLatitude() : deliveryAddr.getLatitude();
                                            Double targetLon = toPickup
                                                    ? pickupAddr.getLongitude() : deliveryAddr.getLongitude();

                                            return presence
                                                    .map(p -> {
                                                        Double curLat = p.getCurrentCoordinates() != null
                                                                ? (double) p.getCurrentCoordinates().latitude()
                                                                : null;
                                                        Double curLon = p.getCurrentCoordinates() != null
                                                                ? (double) p.getCurrentCoordinates().longitude()
                                                                : null;
                                                        Double distanceKm = haversineKm(
                                                                curLat, curLon, targetLat, targetLon);
                                                        Integer eta = distanceKm != null
                                                                ? (int) Math.ceil(distanceKm / 0.4)
                                                                : null;
                                                        return DeliveryAssistanceDTO.builder()
                                                                .deliveryId(delivery.getId())
                                                                .currentStatus(delivery.getStatus())
                                                                .stepDescription(stepDescription(delivery.getStatus()))
                                                                .currentLatitude(curLat)
                                                                .currentLongitude(curLon)
                                                                .targetLatitude(targetLat)
                                                                .targetLongitude(targetLon)
                                                                .distanceKm(distanceKm)
                                                                .estimatedTimeMinutes(eta)
                                                                .build();
                                                    })
                                                    .defaultIfEmpty(DeliveryAssistanceDTO.builder()
                                                            .deliveryId(delivery.getId())
                                                            .currentStatus(delivery.getStatus())
                                                            .stepDescription(stepDescription(delivery.getStatus()))
                                                            .targetLatitude(targetLat)
                                                            .targetLongitude(targetLon)
                                                            .build());
                                        });
                            });
                });
    }

    private static String stepDescription(DeliveryStatus status) {
        if (status == null) {
            return "Statut inconnu — contactez le support si besoin.";
        }
        return switch (status) {
            case CREATED -> "Rendez-vous au point de collecte.";
            case PICKED_UP, IN_TRANSIT -> "Livrez le colis à destination.";
            case AT_RELAY_POINT -> "Colis déposé au point relais — en attente de retrait client.";
            case DELIVERED -> "Livraison terminée.";
            case FAILED -> "Livraison échouée — demandez de l'assistance.";
            case CANCELLED -> "Livraison annulée.";
        };
    }

    private static Double haversineKm(Double lat1, Double lon1, Double lat2, Double lon2) {
        if (lat1 == null || lon1 == null || lat2 == null || lon2 == null) {
            return null;
        }
        double r = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    // ══════════════════════════════════════════════════════════════════════
    // Private — tracking DTO builders
    // ══════════════════════════════════════════════════════════════════════

    /**
     * CREATED  → freelancer position + pickup coordinates (route: freelancer → pickup)
     * PICKED_UP/IN_TRANSIT → freelancer position + delivery coordinates
     * Terminal → coordinates only, no live freelancer position
     */
    private Mono<DeliveryTrackingDTO> buildTrackingDTOByAnnouncementId(UUID announcementId) {
        return announcementRepository.findById(announcementId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException(
                        "Announcement not found: " + announcementId)))
                .flatMap(announcement ->
                        deliveryRepository.findByAnnouncementId(announcementId)
                                .switchIfEmpty(Mono.error(new IllegalArgumentException(
                                        "Delivery not found for announcement: " + announcementId)))
                                .flatMap(delivery ->
                                        deliveryNeedRepository.findById(delivery.getDeliveryNeedId())
                                                .switchIfEmpty(Mono.error(new IllegalArgumentException(
                                                        "DeliveryNeed not found: " + delivery.getDeliveryNeedId())))
                                                .flatMap(deliveryNeed ->
                                                        buildTrackingDTO(announcement, deliveryNeed, delivery))));
    }

    private Mono<DeliveryTrackingDTO> buildTrackingDTOByDeliveryNeedId(UUID deliveryNeedId) {
        return deliveryNeedRepository.findById(deliveryNeedId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException(
                        "DeliveryNeed not found: " + deliveryNeedId)))
                .flatMap(deliveryNeed -> {
                    if (deliveryNeed.getDeliveryId() == null) {
                        return Mono.error(new IllegalStateException(
                                "DeliveryNeed has no associated delivery yet: " + deliveryNeedId));
                    }
                    return deliveryRepository.findById(deliveryNeed.getDeliveryId())
                            .switchIfEmpty(Mono.error(new IllegalArgumentException(
                                    "Delivery not found: " + deliveryNeed.getDeliveryId())))
                            .flatMap(delivery -> buildTrackingDTOFromDeliveryNeed(deliveryNeed, delivery));
                });
    }

    private Mono<DeliveryTrackingDTO> buildTrackingDTO(
            Announcement announcement, DeliveryNeed deliveryNeed, Delivery delivery) {

        DeliveryTrackingDTO dto = new DeliveryTrackingDTO();
        dto.setDeliveryId(delivery.getId());
        dto.setAnnouncementId(announcement.getId());
        dto.setDeliveryNeedId(deliveryNeed.getId());
        dto.setFreelancerId(announcement.getAssignedFreelancerId());
        dto.setStatus(delivery.getStatus()); // status is now a real column on Delivery

        Mono<AddressEntity> pickupAddress = addressRepository.findById(deliveryNeed.getPickupAddressId());
        Mono<AddressEntity> deliveryAddress = addressRepository.findById(deliveryNeed.getDeliveryAddressId());

        Mono<PresenceRecord> freelancerPresence = announcement.getAssignedFreelancerId() != null
                ? getPresenceUseCase.getPresence(
                        announcement.getAssignedFreelancerId().toString(), TENANT_ID_DEFAULT)
                        .onErrorResume(e -> {
                            log.warn("Could not fetch presence for freelancer {}: {}",
                                    announcement.getAssignedFreelancerId(), e.getMessage());
                            return Mono.empty();
                        })
                : Mono.empty();

        return Mono.zip(pickupAddress, deliveryAddress)
                .flatMap(addressTuple -> {
                    dto.setPickupLatitude(addressTuple.getT1().getLatitude());
                    dto.setPickupLongitude(addressTuple.getT1().getLongitude());
                    dto.setDeliveryLatitude(addressTuple.getT2().getLatitude());
                    dto.setDeliveryLongitude(addressTuple.getT2().getLongitude());

                    return freelancerPresence
                            .doOnNext(presence -> {
                                if (presence.getCurrentCoordinates() != null) {
                                    dto.setFreelancerLatitude((float) presence.getCurrentCoordinates().latitude());
                                    dto.setFreelancerLongitude((float) presence.getCurrentCoordinates().longitude());
                                    log.debug("Tracking delivery {} [{}]: freelancer at ({},{}), target: {}",
                                            delivery.getId(), delivery.getStatus(),
                                            dto.getFreelancerLatitude(), dto.getFreelancerLongitude(),
                                            delivery.getStatus() == DeliveryStatus.CREATED ? "pickup" : "delivery");
                                }
                            })
                            .then(Mono.just(dto));
                });
    }

    private Mono<DeliveryTrackingDTO> buildTrackingDTOFromDeliveryNeed(
            DeliveryNeed deliveryNeed, Delivery delivery) {

        DeliveryTrackingDTO dto = new DeliveryTrackingDTO();
        dto.setDeliveryId(delivery.getId());
        dto.setDeliveryNeedId(deliveryNeed.getId());
        dto.setStatus(delivery.getStatus());

        Mono<AddressEntity> pickupAddress = addressRepository.findById(deliveryNeed.getPickupAddressId());
        Mono<AddressEntity> deliveryAddress = addressRepository.findById(deliveryNeed.getDeliveryAddressId());

        return Mono.zip(pickupAddress, deliveryAddress)
                .map(tuple -> {
                    dto.setPickupLatitude(tuple.getT1().getLatitude());
                    dto.setPickupLongitude(tuple.getT1().getLongitude());
                    dto.setDeliveryLatitude(tuple.getT2().getLatitude());
                    dto.setDeliveryLongitude(tuple.getT2().getLongitude());
                    return dto;
                });
    }

    // ══════════════════════════════════════════════════════════════════════
    // Mapper
    // ══════════════════════════════════════════════════════════════════════

    private DeliveryResponseDTO toResponseDTO(Delivery delivery) {
        DeliveryResponseDTO dto = new DeliveryResponseDTO();
        dto.setId(delivery.getId());
        dto.setAnnouncementId(delivery.getAnnouncementId());
        dto.setFreelancerId(delivery.getFreelancerId());
        dto.setStatus(delivery.getStatus());
        dto.setDeliveryNeedId(delivery.getDeliveryNeedId());
        dto.setTarif(delivery.getTarif());
        dto.setNoteLivreur(delivery.getNoteLivreur());
        dto.setPickupMinTime(delivery.getPickupMinTime());
        dto.setPickupMaxTime(delivery.getPickupMaxTime());
        dto.setDeliveryMinTime(delivery.getDeliveryMinTime());
        dto.setDeliveryMaxTime(delivery.getDeliveryMaxTime());
        dto.setDeliveryNote(delivery.getDeliveryNote());
        return dto;
    }
}
