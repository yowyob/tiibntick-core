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
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpFreelancerRepository;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
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
    private final TenantContextHolder tenantContextHolder;
    private final GofpFreelancerRepository gofpFreelancerRepository;

    @Value("${tnt.gofp.ownership-guard.enabled:true}")
    private boolean ownershipGuardEnabled = true;

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
        return tenantContextHolder.currentTenantId()
                .flatMap(tenantId -> {
                    String cacheKey = "tracking:announcement:" + tenantId + ":" + announcementId;
                    return cachePort.get(cacheKey, DeliveryTrackingDTO.class)
                            .switchIfEmpty(buildTrackingDTOByAnnouncementId(announcementId, tenantId)
                                    .flatMap(dto -> cachePort.set(cacheKey, dto, TRACKING_CACHE_TTL).thenReturn(dto)));
                });
    }

    @Override
    public Flux<DeliveryTrackingDTO> trackDeliveryStream(UUID announcementId) {
        // Tenant resolved once at subscription time and captured in the closure;
        // this avoids any uncertainty about context propagation through Flux.interval's
        // parallel scheduler across ticks.
        return tenantContextHolder.currentTenantId()
                .flatMapMany(tenantId ->
                        Flux.interval(Duration.ofSeconds(5))
                                .flatMap(tick -> buildTrackingDTOByAnnouncementId(announcementId, tenantId)
                                        .onErrorResume(e -> {
                                            log.warn("Tracking error for announcement {} (tenant {}): {}",
                                                    announcementId, tenantId, e.getMessage());
                                            return Mono.empty();
                                        }))
                                .distinctUntilChanged());
    }

    @Override
    public Mono<DeliveryTrackingDTO> trackDeliveryByNeed(UUID deliveryNeedId, UUID callerId) {
        return requireTrackingOwnership(deliveryNeedId, callerId)
                .flatMap(need -> tenantContextHolder.currentTenantId()
                        .flatMap(tenantId -> {
                            String cacheKey = "tracking:need:" + tenantId + ":" + deliveryNeedId;
                            return cachePort.get(cacheKey, DeliveryTrackingDTO.class)
                                    .switchIfEmpty(buildTrackingDTOByDeliveryNeedId(deliveryNeedId, tenantId)
                                            .flatMap(dto -> cachePort.set(cacheKey, dto, TRACKING_CACHE_TTL).thenReturn(dto)));
                        }));
    }

    @Override
    public Mono<Void> checkTrackingOwnership(UUID deliveryNeedId, UUID callerId) {
        return requireTrackingOwnership(deliveryNeedId, callerId).then();
    }

    @Override
    public Flux<DeliveryTrackingDTO> trackDeliveryByNeedStream(UUID deliveryNeedId, UUID callerId) {
        return requireTrackingOwnership(deliveryNeedId, callerId)
                .flatMapMany(need -> tenantContextHolder.currentTenantId()
                        .flatMapMany(tenantId ->
                                Flux.interval(Duration.ofSeconds(5))
                                        .flatMap(tick -> buildTrackingDTOByDeliveryNeedId(deliveryNeedId, tenantId)
                                                .onErrorResume(e -> {
                                                    log.warn("Tracking error for delivery need {} (tenant {}): {}",
                                                            deliveryNeedId, tenantId, e.getMessage());
                                                    return Mono.empty();
                                                }))
                                        .distinctUntilChanged()));
    }

    /**
     * Builds navigation assistance for the courier based on delivery status and addresses.
     * Presence lookup failures are ignored (no-op notify / empty GPS).
     *
     * @author MANFOUO BRAUN
     */
    @Override
    public Mono<DeliveryAssistanceDTO> getDeliveryAssistance(UUID id) {
        return tenantContextHolder.currentTenantId()
                .flatMap(tenantId -> {
                    if (TenantContextHolder.SYSTEM_TENANT.equals(tenantId)) {
                        log.warn("getDeliveryAssistance called without HTTP security context for delivery {} — " +
                                "presence lookup will use SYSTEM_TENANT and likely return empty", id);
                    }
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
                                                    ? resolvePresenceUserId(delivery.getFreelancerId())
                                                            .flatMap(userId -> getPresenceUseCase.getPresence(
                                                                            userId, tenantId.toString())
                                                                    .onErrorResume(e -> {
                                                                        log.warn("Assistance presence lookup failed for" +
                                                                                " freelancer {} (tenant {}): {}",
                                                                                delivery.getFreelancerId(), tenantId,
                                                                                e.getMessage());
                                                                        return Mono.empty();
                                                                    }))
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
                                                                    Instant posAt = p.getLastSeenAt() != null
                                                                            ? p.getLastSeenAt().atZone(ZoneId.systemDefault()).toInstant()
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
                                                                            .freelancerPositionAt(posAt)
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
    private Mono<DeliveryTrackingDTO> buildTrackingDTOByAnnouncementId(UUID announcementId, UUID tenantId) {
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
                                                        buildTrackingDTO(announcement, deliveryNeed, delivery, tenantId))));
    }

    /**
     * Ownership guard for tracking routes.
     * Pattern identical to {@code DeliveryNeedApplicationService#requireOwnership}: resolve the
     * need first (404 if absent), then check ownership (403 if mismatch). Never the reverse —
     * returning 403 on an absent need would confirm its existence.
     *
     * <p>{@code callerId == null} is refused, not silently accepted. A non-null authenticated
     * caller whose JWT {@code sub} does not parse as a UUID (platform-client token, API-key) would
     * previously bypass the guard; post-25.1 it gets 403 instead of silent 200.
     */
    private Mono<com.yowyob.tiibntick.core.gofreelancer.domain.model.DeliveryNeed>
            requireTrackingOwnership(UUID deliveryNeedId, UUID callerId) {
        return deliveryNeedRepository.findById(deliveryNeedId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException(
                        "DeliveryNeed not found: " + deliveryNeedId)))
                .flatMap(need -> {
                    if (!ownershipGuardEnabled) return Mono.just(need);
                    if (callerId == null) {
                        return Mono.error(new AccessDeniedException(
                                "[ownership] trackDeliveryByNeed(" + deliveryNeedId + ") requires "
                                + "an authenticated identity (callerId is null — likely a "
                                + "platform-client token with a non-UUID sub)"));
                    }
                    if (!callerId.equals(need.getUserId())) {
                        return Mono.error(new AccessDeniedException(
                                "This delivery need belongs to another user"));
                    }
                    return Mono.just(need);
                });
    }

    private Mono<DeliveryTrackingDTO> buildTrackingDTOByDeliveryNeedId(UUID deliveryNeedId, UUID tenantId) {
        return deliveryNeedRepository.findById(deliveryNeedId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException(
                        "DeliveryNeed not found: " + deliveryNeedId)))
                .flatMap(deliveryNeed -> {
                    if (deliveryNeed.getDeliveryId() == null) {
                        // Delivery not yet created (freelancer assigned but no delivery row yet).
                        // Return a partial DTO with addresses and live position — no delivery status.
                        return buildTrackingDTOFromDeliveryNeed(deliveryNeed, null, tenantId);
                    }
                    return deliveryRepository.findById(deliveryNeed.getDeliveryId())
                            .switchIfEmpty(Mono.error(new IllegalArgumentException(
                                    "Delivery not found: " + deliveryNeed.getDeliveryId())))
                            .flatMap(delivery -> buildTrackingDTOFromDeliveryNeed(deliveryNeed, delivery, tenantId));
                });
    }

    private Mono<DeliveryTrackingDTO> buildTrackingDTO(
            Announcement announcement, DeliveryNeed deliveryNeed, Delivery delivery, UUID tenantId) {

        if (TenantContextHolder.SYSTEM_TENANT.equals(tenantId)) {
            log.warn("buildTrackingDTO called without HTTP security context for announcement {} — " +
                    "presence lookup will use SYSTEM_TENANT and likely return empty", announcement.getId());
        }

        DeliveryTrackingDTO dto = new DeliveryTrackingDTO();
        dto.setDeliveryId(delivery.getId());
        dto.setAnnouncementId(announcement.getId());
        dto.setDeliveryNeedId(deliveryNeed.getId());
        dto.setFreelancerId(announcement.getAssignedFreelancerId());
        dto.setStatus(delivery.getStatus()); // status is now a real column on Delivery

        Mono<AddressEntity> pickupAddress = addressRepository.findById(deliveryNeed.getPickupAddressId());
        Mono<AddressEntity> deliveryAddress = addressRepository.findById(deliveryNeed.getDeliveryAddressId());

        Mono<PresenceRecord> freelancerPresence = announcement.getAssignedFreelancerId() != null
                ? resolvePresenceUserId(announcement.getAssignedFreelancerId())
                        .flatMap(userId -> getPresenceUseCase.getPresence(userId, tenantId.toString())
                                .onErrorResume(e -> {
                                    log.warn("Could not fetch presence for freelancer {} (tenant {}): {}",
                                            announcement.getAssignedFreelancerId(), tenantId, e.getMessage());
                                    return Mono.empty();
                                }))
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
                                if (presence.getLastSeenAt() != null) {
                                    dto.setFreelancerPositionAt(presence.getLastSeenAt().atZone(ZoneId.systemDefault()).toInstant());
                                }
                            })
                            .then(Mono.just(dto));
                });
    }

    /**
     * @param delivery null when no delivery row exists yet (freelancer assigned but trip not started)
     */
    private Mono<DeliveryTrackingDTO> buildTrackingDTOFromDeliveryNeed(
            DeliveryNeed deliveryNeed, Delivery delivery, UUID tenantId) {

        if (TenantContextHolder.SYSTEM_TENANT.equals(tenantId)) {
            log.warn("buildTrackingDTOFromDeliveryNeed called without HTTP security context for need {} — " +
                    "presence lookup will use SYSTEM_TENANT and likely return empty", deliveryNeed.getId());
        }

        DeliveryTrackingDTO dto = new DeliveryTrackingDTO();
        if (delivery != null) {
            dto.setDeliveryId(delivery.getId());
            dto.setStatus(delivery.getStatus());
        }
        dto.setDeliveryNeedId(deliveryNeed.getId());
        dto.setFreelancerId(deliveryNeed.getAssignedFreelancerId());

        Mono<AddressEntity> pickupAddress = addressRepository.findById(deliveryNeed.getPickupAddressId());
        Mono<AddressEntity> deliveryAddress = addressRepository.findById(deliveryNeed.getDeliveryAddressId());

        Mono<PresenceRecord> freelancerPresence = resolvePresenceUserId(deliveryNeed.getAssignedFreelancerId())
                .flatMap(userId -> getPresenceUseCase.getPresence(userId, tenantId.toString())
                        .onErrorResume(e -> {
                            log.warn("Could not fetch presence for need {} (tenant {}): {}",
                                    deliveryNeed.getId(), tenantId, e.getMessage());
                            return Mono.empty();
                        }));

        return Mono.zip(pickupAddress, deliveryAddress)
                .flatMap(tuple -> {
                    dto.setPickupLatitude(tuple.getT1().getLatitude());
                    dto.setPickupLongitude(tuple.getT1().getLongitude());
                    dto.setDeliveryLatitude(tuple.getT2().getLatitude());
                    dto.setDeliveryLongitude(tuple.getT2().getLongitude());

                    return freelancerPresence
                            .doOnNext(presence -> {
                                if (presence.getCurrentCoordinates() != null) {
                                    dto.setFreelancerLatitude((float) presence.getCurrentCoordinates().latitude());
                                    dto.setFreelancerLongitude((float) presence.getCurrentCoordinates().longitude());
                                }
                                if (presence.getLastSeenAt() != null) {
                                    dto.setFreelancerPositionAt(presence.getLastSeenAt().atZone(ZoneId.systemDefault()).toInstant());
                                }
                            })
                            .then(Mono.just(dto));
                });
    }

    /**
     * Resolves the presence userId (= ATANGA coreUserId / JWT sub) from an assignedFreelancerId,
     * which may be either the gofp-local UUID or the coreFreelancerId (tnt-actor-core PK).
     * Returns empty when the local GofpFreelancer mirror does not exist.
     */
    private Mono<String> resolvePresenceUserId(UUID assignedFreelancerId) {
        if (assignedFreelancerId == null) {
            return Mono.empty();
        }
        return gofpFreelancerRepository.findById(assignedFreelancerId)
                .switchIfEmpty(gofpFreelancerRepository.findByCoreFreelancerId(assignedFreelancerId))
                .map(gofp -> gofp.getCoreUserId().toString());
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
