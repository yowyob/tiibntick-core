package com.yowyob.tiibntick.core.delivery.adapter.out.messaging;

import com.yowyob.tiibntick.common.kafka.TntAggregateEvent;
import com.yowyob.tiibntick.common.kafka.TntEventJson;
import com.yowyob.tiibntick.common.kafka.TntEventMasking;
import com.yowyob.tiibntick.core.delivery.domain.event.AggregateEvent;
import com.yowyob.tiibntick.core.delivery.domain.event.AnnouncementSnapshot;
import com.yowyob.tiibntick.core.delivery.domain.event.DeliverySnapshot;
import com.yowyob.tiibntick.core.delivery.domain.event.ParcelSnapshot;
import org.springframework.beans.factory.ObjectProvider;
import java.util.Map;

import com.yowyob.kernel.event.application.port.in.PublishEventBatchUseCase;
import com.yowyob.kernel.event.application.port.in.PublishEventUseCase;
import com.yowyob.kernel.event.domain.model.DomainEventEnvelope;
import com.yowyob.tiibntick.core.delivery.application.port.out.DeliveryEventPublisher;
import com.yowyob.tiibntick.core.delivery.domain.event.DeliveryDomainEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Outbox-backed adapter implementing {@link DeliveryEventPublisher}: each parcel, delivery and announcement
 * {@link AggregateEvent} is enqueued (same transaction as the business write, yow-event-kernel outbox) for its aggregate
 * topic ({@code tnt.parcel.events}, {@code tnt.delivery.events}, {@code tnt.announcement.events}) in the envelope of
 * spec §3.3.
 *
 * <p>Lot L8 (G.5): the double write to the old topics ({@code tnt.delivery.mission.*}, {@code tnt.delivery.package.*},
 * {@code tnt.delivery.announcement.*}, {@code tnt.delivery.freelancer_org.assigned}) is gone, and so are the legacy
 * events that fed it; any other event is ignored (logged).
 *
 * @author MANFOUO Braun
 */
@Slf4j
@Component
public class KafkaDeliveryEventPublisher implements DeliveryEventPublisher {

    private static final String SOLUTION_CODE = "TNT";

    private final PublishEventUseCase publishEventUseCase;
    private final PublishEventBatchUseCase publishEventBatchUseCase;
    private final ObjectProvider<TntEventMasking> masking;

    public KafkaDeliveryEventPublisher(
            PublishEventUseCase publishEventUseCase,
            PublishEventBatchUseCase publishEventBatchUseCase,
            ObjectProvider<TntEventMasking> masking) {
        this.publishEventUseCase = publishEventUseCase;
        this.publishEventBatchUseCase = publishEventBatchUseCase;
        this.masking = masking;
    }

    @Override
    public Mono<Void> publish(DeliveryDomainEvent event) {
        List<DomainEventEnvelope> one = envelopes(event);
        return one.isEmpty() ? Mono.empty() : publishEventUseCase.publish(one.get(0));
    }

    @Override
    public Mono<Void> publishAll(List<DeliveryDomainEvent> events) {
        if (events == null || events.isEmpty()) return Mono.empty();
        List<DomainEventEnvelope> envelopes = events.stream().flatMap(e -> envelopes(e).stream()).toList();
        return envelopes.isEmpty() ? Mono.empty() : publishEventBatchUseCase.publishAll(envelopes).then();
    }

    private List<DomainEventEnvelope> envelopes(DeliveryDomainEvent event) {
        if (event instanceof AggregateEvent aggregateEvent) {
            return aggregateEnvelopes(aggregateEvent);
        }
        log.warn("{} is not an aggregate event: not published", event.getClass().getSimpleName());
        return List.of();
    }

    /**
     * The envelope of an aggregate event, none when it has no tenant: the outbox envelope requires one, and a parcel
     * registered without tenant (P1 of a bare {@code Parcel.create(spec)}) must not fail the operation that saves it.
     */
    private List<DomainEventEnvelope> aggregateEnvelopes(AggregateEvent e) {
        if (e.tenantId() == null) {
            log.warn("{} of {} {} has no tenant: not published", e.eventType(), e.aggregateType(), e.aggregateId());
            return List.of();
        }
        return List.of(toAggregateEnvelope(e));
    }

    /**
     * Envelope of a parcel / delivery / announcement event on its aggregate topic (spec §3.3): payload masked
     * (§3.4), key = aggregate id, {@code previousState} = old status + old values of the changed fields.
     */
    DomainEventEnvelope toAggregateEnvelope(AggregateEvent e) {
        TntEventMasking mask = masking.getIfAvailable(TntEventMasking::withDefaults);
        Map<String, Object> payload = switch (e.snapshot()) {
            case DeliverySnapshot d -> AggregateEventPayloads.delivery(d, mask);
            case ParcelSnapshot p -> AggregateEventPayloads.parcel(p);
            case AnnouncementSnapshot a -> AggregateEventPayloads.announcement(a, mask);
            default -> throw new IllegalArgumentException("Unknown snapshot " + e.snapshot().getClass());
        };
        TntAggregateEvent<Map<String, Object>> event = TntAggregateEvent.<Map<String, Object>>builder(e.aggregateType())
                .eventId(e.eventId())
                .eventType(e.eventType())
                .occurredAt(e.occurredAt())
                .aggregateId(e.aggregateId())
                .aggregateVersion(e.aggregateVersion())
                .tenantId(e.tenantId())
                .correlationId(e.causationId() != null ? e.causationId().toString() : e.eventId().toString())
                .causationId(e.causationId())
                .trigger(e.trigger())
                .actor(e.actor())
                .previousState(e.previousStatus() == null ? null
                        : TntAggregateEvent.previousState(e.previousStatus(),
                                AggregateEventPayloads.plain(e.previousFields())))
                .payload(payload)
                .details(AggregateEventPayloads.plain(e.details()))
                .build();
        return DomainEventEnvelope.wrap()
                .eventId(event.eventId().toString())
                .correlationId(event.correlationId())
                .causationId(event.causationId())
                .eventType(event.eventType())
                .aggregateId(event.aggregateId())
                .aggregateType(event.aggregateType().name())
                .tenantId(event.tenantId())
                .solutionCode(SOLUTION_CODE)
                .payload(TntEventJson.write(event))
                .schemaVersion(event.schemaVersion())
                .kafkaTopic(event.topic())
                .occurredAt(LocalDateTime.ofInstant(event.occurredAt(), ZoneOffset.UTC))
                .build();
    }

}
