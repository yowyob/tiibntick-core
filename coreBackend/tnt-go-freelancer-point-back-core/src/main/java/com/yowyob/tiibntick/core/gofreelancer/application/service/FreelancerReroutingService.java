package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.realtime.application.port.in.IBroadcastEtaUseCase;
import com.yowyob.tiibntick.core.realtime.domain.model.ReroutingAlert;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.Delivery;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryStatus;
import com.yowyob.tiibntick.core.gofreelancer.domain.port.out.DeliveryRepository;
import com.yowyob.tiibntick.core.gofreelancer.domain.port.out.PushNotificationPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Application service bridging the freelancer delivery module with the rerouting
 * capabilities of {@code tnt-realtime-core}.
 *
 * <p>All rerouting operations are scoped to an existing {@link Delivery}
 * that must be in {@code IN_TRANSIT} status.</p>
 *
 * @author François-Charles ATANGA
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FreelancerReroutingService {

    private final IBroadcastEtaUseCase broadcastEtaUseCase;
    private final PushNotificationPort pushNotificationPort;
    private final DeliveryRepository deliveryRepository;
    private final com.yowyob.tiibntick.core.delivery.application.port.in.DeliveryQueryUseCase deliveryQueryUseCase;

    /**
     * Redirects a freelancer during an active delivery.
     *
     * <p>Validates that the delivery exists and is currently {@code IN_TRANSIT},
     * then broadcasts the rerouting alert via WebSocket and sends a Push notification
     * to the freelancer.</p>
     *
     * @param deliveryId  UUID of the delivery to reroute
     * @param tenantId    the tenant context
     * @param newRouteId  the new route ID (from tnt-route-core)
     * @param reason      human-readable reason for the reroute
     * @return Mono completing when broadcast and notification are dispatched
     */
    public Mono<Void> rerouteDelivery(UUID deliveryId, String tenantId,
                                       String newRouteId, String reason) {

        return deliveryQueryUseCase.findDeliveryById(TenantContextHolder.SYSTEM_TENANT, deliveryId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException(
                        "Livraison introuvable : " + deliveryId)))
                .flatMap(coreDelivery -> {
                    // Only IN_TRANSIT deliveries can be rerouted
                    if (!DeliveryStatus.IN_TRANSIT.equals(coreDelivery.getStatus())) {
                        return Mono.error(new IllegalStateException(
                                "Impossible de rediriger la livraison " + deliveryId +
                                        " : statut actuel = " + coreDelivery.getStatus() +
                                        ". Seules les livraisons EN TRANSIT peuvent être redirigées."));
                    }

                    UUID freelancerId = coreDelivery.getDeliveryPersonId();
                    
                    log.info("Rerouting delivery {} (freelancer={}) — reason: {}",
                            deliveryId, freelancerId, reason);

                    String missionId = deliveryId.toString();

                    ReroutingAlert alert = ReroutingAlert.of(
                            missionId,
                            freelancerId != null ? freelancerId.toString() : "UNKNOWN",
                            tenantId,
                            null, // oldRouteId unknown for manual triggers
                            newRouteId,
                            0.0, 0.0, 0,
                            reason
                    );

                    // 1. Broadcast via WebSocket (realtime-core handles topic routing)
                    Mono<Void> broadcast = broadcastEtaUseCase.broadcastReroutingAlert(alert);

                    // 2. Push notification for immediate device-level awareness
                    Mono<Void> pushNotify = freelancerId == null ? Mono.empty() : pushNotificationPort.sendPushNotification(
                            freelancerId,
                            "Itinéraire Mis à Jour !",
                            "Votre itinéraire pour la livraison en cours a été modifié. Raison : " + reason
                    ).onErrorResume(e -> {
                        log.error("Failed to send push notification for delivery reroute {}", deliveryId, e);
                        return Mono.empty();
                    });

                    return Mono.when(broadcast, pushNotify);
                });
    }

    /**
     * Handles an automatic rerouting alert from tnt-route-core for a delivery.
     *
     * <p>Validates that the delivery is still in transit before broadcasting.</p>
     *
     * @param alert      the rerouting alert from tnt-route-core
     * @param deliveryId the associated delivery UUID
     * @return Mono completing when broadcast and notification are dispatched
     */
    public Mono<Void> handleAutomaticReroutingAlert(ReroutingAlert alert, UUID deliveryId) {
        return deliveryQueryUseCase.findDeliveryById(TenantContextHolder.SYSTEM_TENANT, deliveryId)
                .filter(coreDelivery -> DeliveryStatus.IN_TRANSIT.equals(coreDelivery.getStatus()))
                .flatMap(coreDelivery -> {
                    UUID freelancerId = coreDelivery.getDeliveryPersonId();
                    
                    log.info("Processing automatic reroute for delivery {} — saving {}km / {}min",
                            deliveryId,
                            String.format("%.1f", alert.distanceSavedKm()),
                            alert.timeSavedMin());

                    Mono<Void> broadcast = broadcastEtaUseCase.broadcastReroutingAlert(alert);

                    Mono<Void> pushNotify = freelancerId == null ? Mono.empty() : pushNotificationPort.sendPushNotification(
                            freelancerId,
                            "Meilleur Itinéraire Trouvé !",
                            "Un itinéraire optimisé a été trouvé pour votre livraison. " +
                                    "Économie : " + String.format("%.1f", alert.distanceSavedKm()) + " km, " +
                                    alert.timeSavedMin() + " min gagnées."
                    ).onErrorResume(e -> {
                        log.error("Failed to send push notification for auto-reroute on delivery {}", deliveryId, e);
                        return Mono.empty();
                    });

                    return Mono.when(broadcast, pushNotify);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("Ignoring reroute alert for delivery {} — not found or not IN_TRANSIT", deliveryId);
                    return Mono.empty();
                }));
    }
}
