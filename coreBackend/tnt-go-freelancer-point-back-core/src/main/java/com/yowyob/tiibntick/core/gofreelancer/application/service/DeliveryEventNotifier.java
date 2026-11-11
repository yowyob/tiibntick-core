package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.delivery.application.port.in.DeliveryQueryUseCase;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.DeliveryRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IAnnouncementRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.PushNotificationPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Tells the people involved in a delivery what happened to it, whoever caused it: the livreur's own
 * tap, the client, an incident, an SLA timeout. Fed by the core's mission status events, so every
 * origin is covered by the same path.
 *
 * <p>Recipients are the kernel actor ids (= the user ids of the JWT), which is what the notification
 * feed endpoints authorise: the client is the announcement's {@code clientId}; the livreur is the
 * actor behind the delivery's freelancer profile.
 *
 * <p>An event id already handled is skipped (Kafka redelivers at least once), so a status never
 * produces two notifications for the same person.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryEventNotifier {

    private static final int REMEMBERED_EVENTS = 5_000;

    private final DeliveryRepository deliveryRepository;
    private final IAnnouncementRepository announcementRepository;
    private final DeliveryQueryUseCase deliveryQueryUseCase;
    private final PushNotificationPort pushNotificationPort;

    private final Set<UUID> handledEvents = Collections.newSetFromMap(Collections.synchronizedMap(
            new LinkedHashMap<>() {
                @Override
                protected boolean removeEldestEntry(Map.Entry<UUID, Boolean> eldest) {
                    return size() > REMEMBERED_EVENTS;
                }
            }));

    /** Who is told what, for a given new status. */
    record Message(String title, String body, boolean client, boolean driver) {}

    static Optional<Message> messageFor(String newStatus, String trackingCode) {
        String ref = trackingCode != null && !trackingCode.isBlank() ? " " + trackingCode : "";
        return switch (newStatus) {
            case "PICKED_UP" -> Optional.of(new Message("Colis récupéré",
                    "Votre colis" + ref + " a été récupéré par le livreur.", true, false));
            case "IN_TRANSIT" -> Optional.of(new Message("Colis en route",
                    "Votre colis" + ref + " est en cours de livraison.", true, false));
            case "AT_RELAY_POINT" -> Optional.of(new Message("Colis au point relais",
                    "Votre colis" + ref + " a été déposé dans un point relais.", true, false));
            case "DELIVERED" -> Optional.of(new Message("Colis livré",
                    "Votre colis" + ref + " a été livré.", true, false));
            case "FAILED" -> Optional.of(new Message("Livraison échouée",
                    "La livraison du colis" + ref + " n'a pas pu aboutir.", true, false));
            case "CANCELLED" -> Optional.of(new Message("Livraison annulée",
                    "La livraison du colis" + ref + " a été annulée.", true, true));
            case "PAUSED_BY_INCIDENT" -> Optional.of(new Message("Livraison suspendue",
                    "La livraison du colis" + ref + " est suspendue à cause d'un incident. "
                            + "Nous revenons vers vous dès sa résolution.", true, true));
            case "TIMED_OUT", "SLA_BREACHED" -> Optional.of(new Message("Livraison en retard",
                    "La livraison du colis" + ref + " dépasse le délai prévu.", true, true));
            default -> Optional.empty();
        };
    }

    /**
     * @return the number of notifications handed to the notification module (0 when the event is a
     *         replay, concerns no gofp delivery, or the status is not worth a notification)
     */
    public Mono<Integer> onMissionStatusChanged(UUID eventId, UUID deliveryId, UUID tenantId,
                                                String newStatus, String trackingCode) {
        Optional<Message> message = messageFor(newStatus, trackingCode);
        if (message.isEmpty()) {
            return Mono.just(0);
        }
        if (eventId != null && !handledEvents.add(eventId)) {
            log.debug("Mission event {} already notified, skipping", eventId);
            return Mono.just(0);
        }
        Message m = message.get();
        return deliveryRepository.findById(deliveryId).flatMap(delivery -> {
            Mono<UUID> client = !m.client() || delivery.getAnnouncementId() == null ? Mono.empty()
                    : announcementRepository.findById(delivery.getAnnouncementId())
                            .mapNotNull(a -> a.getClientId());
            Mono<UUID> driver = !m.driver() || delivery.getFreelancerId() == null ? Mono.empty()
                    : deliveryQueryUseCase.resolveActorIdForDeliveryPerson(tenantId, delivery.getFreelancerId())
                            .onErrorResume(e -> {
                                log.warn("No actor for delivery person {} of delivery {}: {}",
                                        delivery.getFreelancerId(), deliveryId, e.getMessage());
                                return Mono.empty();
                            });
            return Mono.zip(client.map(Optional::of).defaultIfEmpty(Optional.empty()),
                            driver.map(Optional::of).defaultIfEmpty(Optional.empty()))
                    .flatMap(t -> {
                        Set<UUID> recipients = new java.util.LinkedHashSet<>();
                        t.getT1().ifPresent(recipients::add);
                        t.getT2().ifPresent(recipients::add);
                        return Mono.just(recipients);
                    })
                    .flatMap(recipients -> reactor.core.publisher.Flux.fromIterable(recipients)
                            .flatMap(r -> pushNotificationPort.sendPushNotification(tenantId, r, m.title(), m.body())
                                    .onErrorResume(e -> {
                                        log.warn("Notification to {} for delivery {} failed: {}", r, deliveryId, e.getMessage());
                                        return Mono.empty();
                                    }))
                            .then(Mono.just(recipients.size())));
        }).defaultIfEmpty(0);
    }
}
