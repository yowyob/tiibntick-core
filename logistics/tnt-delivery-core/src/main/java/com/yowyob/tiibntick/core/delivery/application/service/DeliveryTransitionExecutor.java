package com.yowyob.tiibntick.core.delivery.application.service;

import com.yowyob.tiibntick.core.delivery.application.port.out.DeliveryAnnouncementRepository;
import com.yowyob.tiibntick.core.delivery.application.port.out.DeliveryEventPublisher;
import com.yowyob.tiibntick.core.delivery.application.port.out.DeliveryRepository;
import com.yowyob.tiibntick.core.delivery.domain.event.AggregateEvent;
import com.yowyob.tiibntick.core.delivery.domain.event.DeliveryDomainEvent;
import com.yowyob.tiibntick.core.delivery.domain.event.EventSource;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Delivery;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.DeliveryAnnouncement;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.AnnouncementOutcome;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.AnnouncementStatus;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryStatus;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.ParcelStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

/**
 * The one place where a delivery transition is persisted and published (spec §1.0 rule 5): in the caller's
 * transaction it saves the parcel and the delivery, applies the effects on the announcement (A6 cancelled with its
 * delivery, A7 completed with the outcome, QO-9) and opens the return draft when the parcel must go back (QO-4), then
 * hands every recorded event — delivery, parcel, announcement, return draft — to the outbox.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeliveryTransitionExecutor {

    private final DeliveryRepository deliveryRepository;
    private final DeliveryAnnouncementRepository announcementRepository;
    private final DeliveryEventPublisher eventPublisher;
    private final ReturnDeliveryService returnDeliveryService;

    /** Return guarantee of deliveries to a relay hub (D-147, D-151). Optional so that unit tests can omit it. */
    private ReturnGuaranteeService returnGuarantee;

    @Autowired(required = false)
    public void setReturnGuarantee(ReturnGuaranteeService returnGuarantee) {
        this.returnGuarantee = returnGuarantee;
    }

    /** Freezes the return guarantee of a delivery just priced, when it is due (D-147, D-151); before its save. */
    public Mono<Delivery> holdReturnGuarantee(Delivery delivery) {
        return returnGuarantee == null ? Mono.just(delivery) : returnGuarantee.holdIfDue(delivery);
    }

    /**
     * Applies the return guarantee effect (D-147, D-151), saves the delivery (and its parcel), applies the follow-up
     * effects and publishes every event — in the caller's
     * transaction, or in a new one when called outside (scheduler, Kafka consumer).
     */
    @Transactional
    public Mono<Delivery> saveAndPublish(Delivery delivery) {
        List<AggregateEvent> transitions = delivery.getAggregateEvents().stream()
                .filter(AggregateEvent::isTransition).toList();
        // QO-4: the parcel just became RETURN_REQUESTED, or (D-148) its return was cancelled before pickup
        boolean returnRequested = delivery.getParcel() != null && delivery.getParcel().getDomainEvents().stream()
                .anyMatch(e -> e instanceof AggregateEvent ae
                        && (ae.isTransition() && "ParcelReturnRequested".equals(ae.eventType())
                            || "ParcelReturnDeliveryCancelled".equals(ae.eventType())));
        Mono<Delivery> guaranteed = returnGuarantee == null ? Mono.just(delivery)
                : returnGuarantee.onTransitions(delivery, transitions);
        return guaranteed.flatMap(deliveryRepository::save)
                .flatMap(saved -> announcementEffect(saved, transitions)
                        .map(List::of).defaultIfEmpty(List.of())
                        .flatMap(announcements -> (returnRequested
                                ? returnDeliveryService.openReturnDraft(saved).map(List::of).defaultIfEmpty(List.of())
                                : Mono.just(List.<DeliveryAnnouncement>of()))
                                .flatMap(drafts -> publish(saved, announcements, drafts))));
    }

    /** Publishes the events of the given aggregates (each once) and clears them. */
    public Mono<Void> publish(EventSource... sources) {
        // Lazy: the events are drained at subscription, once the saves chained before have run.
        return Mono.defer(() -> {
            List<DeliveryDomainEvent> events = EventSource.drain(sources);
            return events.isEmpty() ? Mono.<Void>empty() : eventPublisher.publishAll(events);
        });
    }

    private Mono<Delivery> publish(Delivery saved, List<DeliveryAnnouncement> announcements,
                                   List<DeliveryAnnouncement> drafts) {
        List<EventSource> sources = new ArrayList<>();
        sources.add(saved);
        sources.addAll(announcements);
        sources.addAll(drafts);
        return publish(sources.toArray(EventSource[]::new)).thenReturn(saved);
    }

    /** A6 / A7 on the announcement of a delivery that reached a final state in this operation. */
    private Mono<DeliveryAnnouncement> announcementEffect(Delivery delivery, List<AggregateEvent> transitions) {
        if (delivery.getAnnouncementId() == null || !delivery.getStatus().isTerminal()
                || transitions.stream().noneMatch(e -> isFinal(e.eventType()))) {
            return Mono.empty();
        }
        return announcementRepository.findById(delivery.getTenantId(), delivery.getAnnouncementId())
                .filter(a -> a.getStatus() == AnnouncementStatus.ASSIGNED)
                .flatMap(a -> {
                    if (delivery.getStatus() == DeliveryStatus.CANCELLED) {
                        a.cancelWithDelivery();
                    } else {
                        a.complete(delivery.getStatus() == DeliveryStatus.DELIVERED
                                ? AnnouncementOutcome.DELIVERED : AnnouncementOutcome.FAILED);
                    }
                    return announcementRepository.save(a);
                });
    }

    private static boolean isFinal(String eventType) {
        return "DeliveryDelivered".equals(eventType) || "DeliveryFailed".equals(eventType)
                || "DeliveryCancelled".equals(eventType);
    }

    /** @return whether the parcel of the delivery waits for a return */
    static boolean awaitsReturn(Delivery delivery) {
        return delivery.getParcel() != null && delivery.getParcel().getStatus() == ParcelStatus.RETURN_REQUESTED;
    }
}
