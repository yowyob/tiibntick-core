package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.delivery.application.port.in.DeliveryQueryUseCase;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.DeliveryRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IAnnouncementRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.PushNotificationPort;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpNotificationTemplates;
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

    /** Who is told what, for a given new status: a catalog message (rendered in each recipient's language). */
    record Message(String templateKey, Map<String, Object> params, boolean client, boolean driver) {}

    /** Lot L8: whether a delivery entering this status is notified (the caller then resolves the tracking code). */
    public static boolean isNotified(String newStatus) {
        return newStatus != null && messageFor(newStatus, null).isPresent();
    }

    static Optional<Message> messageFor(String newStatus, String trackingCode) {
        // " TNT-42" so that the catalog text reads "Votre colis TNT-42 ..." or "Votre colis ..." without a code.
        Map<String, Object> params = Map.of("ref", trackingCode != null && !trackingCode.isBlank()
                ? " " + trackingCode : "");
        return switch (newStatus) {
            case "PICKED_UP" -> Optional.of(new Message(GofpNotificationTemplates.DELIVERY_PICKED_UP, params, true, false));
            case "IN_TRANSIT" -> Optional.of(new Message(GofpNotificationTemplates.DELIVERY_IN_TRANSIT, params, true, false));
            // lot L8 (spec §2.3): AT_RELAY_POINT is gone — only aggregate events (AT_RELAY_HUB) reach this notifier
            case "AT_RELAY_HUB" -> Optional.of(new Message(GofpNotificationTemplates.DELIVERY_AT_RELAY_HUB, params, true, false));
            case "DELIVERED" -> Optional.of(new Message(GofpNotificationTemplates.DELIVERY_DELIVERED, params, true, false));
            case "FAILED" -> Optional.of(new Message(GofpNotificationTemplates.DELIVERY_FAILED, params, true, false));
            case "CANCELLED" -> Optional.of(new Message(GofpNotificationTemplates.DELIVERY_CANCELLED, params, true, true));
            case "PAUSED_BY_INCIDENT" -> Optional.of(new Message(GofpNotificationTemplates.DELIVERY_PAUSED, params, true, true));
            // TIMED_OUT / SLA_BREACHED are no longer states (decision [C.3]): lateness is the DeliveryAlertRaised event
            // (alertType DELIVERY_LATE) of tnt.delivery.events, notified by onDeliveryLate (lot L3).
            default -> Optional.empty();
        };
    }

    /**
     * @return the number of notifications handed to the notification module (0 when the event is a
     *         replay, concerns no gofp delivery, or the status is not worth a notification)
     */
    public Mono<Integer> onDeliveryStatusChanged(UUID eventId, UUID deliveryId, UUID tenantId,
                                                String newStatus, String trackingCode) {
        Optional<Message> message = messageFor(newStatus, trackingCode);
        if (message.isEmpty()) {
            return Mono.just(0);
        }
        return notify(eventId, deliveryId, tenantId, message.get());
    }

    /**
     * {@code DeliveryAlertRaised(DELIVERY_LATE)} on {@code tnt.delivery.events} (lot L3, QO-18): the client and the
     * courier are told that the delivery is late.
     *
     * @return the number of notifications handed to the notification module
     */
    public Mono<Integer> onDeliveryLate(UUID eventId, UUID deliveryId, UUID tenantId, String trackingCode) {
        Map<String, Object> params = Map.of("ref", trackingCode != null && !trackingCode.isBlank()
                ? " " + trackingCode : "");
        return notify(eventId, deliveryId, tenantId,
                new Message(GofpNotificationTemplates.DELIVERY_LATE, params, true, true));
    }

    private Mono<Integer> notify(UUID eventId, UUID deliveryId, UUID tenantId, Message m) {
        if (eventId != null && !handledEvents.add(eventId)) {
            log.debug("Delivery event {} already notified, skipping", eventId);
            return Mono.just(0);
        }
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
                            .flatMap(r -> pushNotificationPort.sendLocalized(tenantId, r, m.templateKey(), m.params())
                                    .onErrorResume(e -> {
                                        log.warn("Notification to {} for delivery {} failed: {}", r, deliveryId, e.getMessage());
                                        return Mono.empty();
                                    }))
                            .then(Mono.just(recipients.size())));
        }).defaultIfEmpty(0);
    }
}
