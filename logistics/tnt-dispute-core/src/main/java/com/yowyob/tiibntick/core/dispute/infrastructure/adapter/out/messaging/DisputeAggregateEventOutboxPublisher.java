package com.yowyob.tiibntick.core.dispute.infrastructure.adapter.out.messaging;

import com.yowyob.kernel.event.application.port.in.PublishEventBatchUseCase;
import com.yowyob.kernel.event.domain.model.DomainEventEnvelope;
import com.yowyob.tiibntick.common.kafka.TntAggregateEvent;
import com.yowyob.tiibntick.common.kafka.TntAggregateType;
import com.yowyob.tiibntick.common.kafka.TntEventJson;
import com.yowyob.tiibntick.common.tenant.SystemTenant;
import com.yowyob.tiibntick.core.dispute.application.port.outbound.IDisputeAggregateEventPublisher;
import com.yowyob.tiibntick.core.dispute.domain.model.Dispute;
import com.yowyob.tiibntick.core.dispute.domain.model.DisputeTransition;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * {@link IDisputeAggregateEventPublisher} over the outbox (lot L4): one {@code Dispute<State>} event per transition
 * LT1–LT12 and {@code DisputeEvidenceAdded}. Key = dispute id; {@code aggregateVersion} = the dispute's event
 * sequence; payload masked (spec §3.4: no description, summary, comment nor evidence content). A dispute whose tenant
 * is not a UUID is published in the system tenant (decision D-150).
 */
@Component
public class DisputeAggregateEventOutboxPublisher implements IDisputeAggregateEventPublisher {

    static final String SOLUTION_CODE = "TNT";

    private final PublishEventBatchUseCase publishBatch;

    public DisputeAggregateEventOutboxPublisher(PublishEventBatchUseCase publishBatch) {
        this.publishBatch = publishBatch;
    }

    @Override
    public Mono<Void> publish(Dispute dispute) {
        if (dispute.getPendingTransitions().isEmpty()) {
            return Mono.empty();
        }
        return Mono.fromCallable(() -> envelopes(dispute)).flatMap(publishBatch::publishAll).then();
    }

    List<DomainEventEnvelope> envelopes(Dispute dispute) {
        List<DisputeTransition> pending = dispute.getPendingTransitions();
        Map<String, Object> payload = payload(dispute);
        UUID tenant = uuid(dispute.getTenantId());
        String tenantId = (tenant != null ? tenant : SystemTenant.id()).toString();
        long firstVersion = dispute.getEventSequence() - pending.size() + 1;
        List<DomainEventEnvelope> out = new ArrayList<>(pending.size());
        UUID causation = null;
        for (int i = 0; i < pending.size(); i++) {
            DisputeTransition t = pending.get(i);
            UUID eventId = UUID.randomUUID();
            TntAggregateEvent<Map<String, Object>> event = TntAggregateEvent.<Map<String, Object>>builder(
                            TntAggregateType.DISPUTE)
                    .eventId(eventId)
                    .eventType(TntAggregateType.DISPUTE.eventTypeFor(t.eventKind()))
                    .occurredAt(t.at().truncatedTo(ChronoUnit.MILLIS))
                    .aggregateId(dispute.getId().getValue())
                    .aggregateVersion(Math.max(0, firstVersion + i))
                    .tenantId(tenantId)
                    .correlationId(dispute.getId().getValue())
                    .causationId(causation)
                    .trigger(t.trigger())
                    .actor(t.actor())
                    .previousState(t.from() == null ? null : TntAggregateEvent.previousState(t.from().name(), null))
                    .payload(payload)
                    .details(t.details())
                    .build();
            causation = eventId;
            out.add(DomainEventEnvelope.wrap()
                    .eventId(eventId.toString())
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
                    .build());
        }
        return out;
    }

    /** Full masked state of the dispute after the changes (spec §3.6 {@code dispute.payload}). */
    static Map<String, Object> payload(Dispute d) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("disputeId", d.getId().getValue());
        p.put("reference", d.getReference() != null ? d.getReference().getValue() : null);
        p.put("parcelId", uuidString(d.getPackageId()));
        p.put("deliveryId", uuidString(d.getMissionId()));
        p.put("incidentId", uuidString(d.getIncidentId()));
        p.put("cause", name(d.getCause()));
        p.put("category", name(d.getCategory()));
        p.put("priority", name(d.getPriority()));
        p.put("status", d.getStatus().name());
        p.put("claimantType", name(d.getClaimantType()));
        p.put("claimantId", uuidString(d.getClaimantId()));
        p.put("respondentType", name(d.getRespondentType()));
        p.put("mediatorId", uuidString(d.getAssignedMediatorId()));
        p.put("resolutionType", d.getResolution() != null ? name(d.getResolution().getType()) : null);
        p.put("responsibleParty", name(d.getResponsibleParty()));
        p.put("compensationAmount", d.getCompensation() != null && d.getCompensation().getAmount() != null
                ? d.getCompensation().getAmount().toPlainString() : null);
        p.put("currency", d.getCompensation() != null ? d.getCompensation().getCurrency() : null);
        p.put("compensationPaymentRef", d.getCompensation() != null ? d.getCompensation().getPaymentReference() : null);
        p.put("evidenceCount", d.getEvidences().size());
        p.put("deadline", time(d.getDeadline()));
        p.put("openedAt", time(d.getFiledAt()));
        p.put("resolvedAt", d.getResolution() != null ? time(d.getResolution().getOccurredAt()) : null);
        p.put("closedAt", time(d.getClosedAt()));
        return p;
    }

    private static String name(Enum<?> e) {
        return e == null ? null : e.name();
    }

    /** Times of the dispute are local times of the server, written as UTC instants (spec §3.2). */
    private static String time(LocalDateTime t) {
        return t == null ? null : t.atZone(java.time.ZoneId.systemDefault()).toInstant()
                .truncatedTo(ChronoUnit.MILLIS).toString();
    }

    private static String uuidString(String value) {
        UUID id = uuid(value);
        return id == null ? null : id.toString();
    }

    private static UUID uuid(String value) {
        try {
            return value == null || value.isBlank() ? null : UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
