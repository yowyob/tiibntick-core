package com.yowyob.tiibntick.core.incident.adapter.kafka;

import com.yowyob.kernel.event.application.port.in.PublishEventBatchUseCase;
import com.yowyob.kernel.event.domain.model.DomainEventEnvelope;
import com.yowyob.tiibntick.common.kafka.TntAggregateEvent;
import com.yowyob.tiibntick.common.kafka.TntAggregateType;
import com.yowyob.tiibntick.common.kafka.TntEventJson;
import com.yowyob.tiibntick.common.kafka.TntEventMasking;
import com.yowyob.tiibntick.common.tenant.SystemTenant;
import com.yowyob.tiibntick.core.incident.domain.model.Incident;
import com.yowyob.tiibntick.core.incident.domain.model.IncidentTransition;
import com.yowyob.tiibntick.core.incident.port.outbound.IIncidentAggregateEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * {@link IIncidentAggregateEventPublisher} over the outbox: one {@code Incident<State>} event per transition, plus the
 * changes without transition ({@code IncidentEscalatedToDispute}, {@code IncidentHandoverConfirmed},
 * {@code IncidentHandlerAssigned}). Key = incident id; {@code aggregateVersion} = the incident's event sequence;
 * payload masked (spec §3.4: no description, position rounded). An incident without tenant is published in the system
 * tenant (decision D-150).
 */
@Slf4j
@Component
public class IncidentAggregateEventOutboxPublisher implements IIncidentAggregateEventPublisher {

    static final String SOLUTION_CODE = "TNT";

    private final PublishEventBatchUseCase publishBatch;
    private final ObjectProvider<TntEventMasking> masking;

    public IncidentAggregateEventOutboxPublisher(PublishEventBatchUseCase publishBatch,
                                                 ObjectProvider<TntEventMasking> masking) {
        this.publishBatch = publishBatch;
        this.masking = masking;
    }

    @Override
    public Mono<Void> publish(Incident saved, Incident withPendingTransitions) {
        List<IncidentTransition> pending = withPendingTransitions.getPendingTransitions();
        if (pending.isEmpty()) {
            return Mono.empty();
        }
        return Mono.fromCallable(() -> envelopes(saved, pending))
                .flatMap(publishBatch::publishAll)
                .then();
    }

    List<DomainEventEnvelope> envelopes(Incident saved, List<IncidentTransition> pending) {
        Map<String, Object> payload = payload(saved, masking.getIfAvailable(TntEventMasking::withDefaults));
        String tenant = (saved.getTenantId() != null ? saved.getTenantId() : SystemTenant.id()).toString();
        long firstVersion = saved.getEventSequence() - pending.size() + 1;
        List<DomainEventEnvelope> out = new ArrayList<>(pending.size());
        UUID causation = null;
        for (int i = 0; i < pending.size(); i++) {
            IncidentTransition t = pending.get(i);
            UUID eventId = UUID.randomUUID();
            TntAggregateEvent<Map<String, Object>> event = TntAggregateEvent.<Map<String, Object>>builder(
                            TntAggregateType.INCIDENT)
                    .eventId(eventId)
                    .eventType(TntAggregateType.INCIDENT.eventTypeFor(t.eventKind()))
                    .occurredAt(t.at().truncatedTo(java.time.temporal.ChronoUnit.MILLIS))
                    .aggregateId(saved.getId().toString())
                    .aggregateVersion(Math.max(0, firstVersion + i))
                    .tenantId(tenant)
                    .correlationId(saved.getId().toString())
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

    /** Full masked state of the incident after the changes (spec §3.4: no description, position rounded). */
    static Map<String, Object> payload(Incident i, TntEventMasking mask) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("incidentId", i.getId().toString());
        p.put("referenceCode", i.getReferenceCode());
        p.put("deliveryId", str(i.getMissionId()));
        p.put("trackingCode", i.getTrackingCode());
        p.put("agencyId", str(i.getAgencyId()));
        p.put("platform", name(i.getSourcePlatform()));
        p.put("status", i.getStatus().name());
        p.put("type", name(i.getType()));
        p.put("category", name(i.getCategory()));
        p.put("severity", name(i.getSeverity()));
        p.put("blocking", i.isBlocking());
        p.put("deliveryOutcome", name(i.getDeliveryOutcome()));
        p.put("responsibleParty", name(i.getResponsibleParty()));
        p.put("escalationLevel", i.getLastEscalationLevel());
        p.put("reportedByActorId", str(i.getReportedByActorId()));
        p.put("reportedByRole", name(i.getReportedByRole()));
        p.put("affectedParcelIds", i.getAffectedParcelIds() == null ? List.of()
                : i.getAffectedParcelIds().stream().map(UUID::toString).toList());
        boolean hasPosition = i.getGeoSnapshot() != null
                && !(i.getGeoSnapshot().getLatitude() == 0.0 && i.getGeoSnapshot().getLongitude() == 0.0);
        p.put("approxLatitude", hasPosition ? mask.approx(i.getGeoSnapshot().getLatitude()) : null);
        p.put("approxLongitude", hasPosition ? mask.approx(i.getGeoSnapshot().getLongitude()) : null);
        p.put("resolutionProposedAt", time(i.getResolutionProposedAt()));
        p.put("reportedAt", time(i.getReportedAt()));
        p.put("resolvedAt", time(i.getResolvedAt()));
        p.put("closedAt", time(i.getClosedAt()));
        return p;
    }

    private static String str(UUID id) {
        return id == null ? null : id.toString();
    }

    private static String name(Enum<?> e) {
        return e == null ? null : e.name();
    }

    private static String time(Instant t) {
        return t == null ? null : t.truncatedTo(java.time.temporal.ChronoUnit.MILLIS).toString();
    }
}
